package ai.jadebase;

import ai.jadebase.connector.feishu.FeishuConnection;
import ai.jadebase.connector.feishu.FeishuConnectorService;
import ai.jadebase.connector.feishu.FeishuRemoteDocumentRepository;
import ai.jadebase.connector.feishu.FeishuConnectionRepository;
import ai.jadebase.connector.feishu.FeishuSource;
import ai.jadebase.connector.feishu.FeishuSyncExecutor;
import ai.jadebase.connector.feishu.FeishuSyncTask;
import ai.jadebase.connector.feishu.FeishuSyncTaskRepository;
import ai.jadebase.knowledge.domain.Document;
import ai.jadebase.knowledge.domain.KnowledgeBase;
import ai.jadebase.knowledge.domain.KnowledgeBaseService;
import ai.jadebase.knowledge.infra.DocumentRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jadebase-feishu-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "jadebase.connectors.feishu.allow-custom-base-url=true",
        "jadebase.connectors.feishu.poll-delay-ms=600000",
        "jadebase.connectors.feishu.recovery-delay-ms=600000",
        "jadebase.indexing.poll-delay-ms=20"
})
class FeishuConnectorIntegrationTest {

    @Autowired
    FeishuConnectorService connectors;
    @Autowired
    FeishuSyncExecutor sync;
    @Autowired
    FeishuSyncTaskRepository tasks;
    @Autowired
    FeishuRemoteDocumentRepository remoteDocuments;
    @Autowired
    FeishuConnectionRepository connectionRepository;
    @Autowired
    KnowledgeBaseService knowledgeBases;
    @Autowired
    DocumentRepository documents;

    private HttpServer server;
    private String apiBaseUrl;
    private final AtomicInteger stage = new AtomicInteger(1);
    private final AtomicBoolean throttleAuthentication = new AtomicBoolean(true);

    @BeforeEach
    void startFeishuApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/open-apis/", this::handle);
        server.start();
        apiBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/open-apis";
    }

    @AfterEach
    void stopFeishuApi() {
        server.stop(0);
    }

    @Test
    void syncsWikiIncrementallyWithoutDuplicatesAndReflectsMovesAndDeletes() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBases.create("飞书研发空间", "连接器集成测试");
        var connection = connectors.create(new FeishuConnectorService.ConnectionInput(
                "测试飞书", "cli_test", "secret", apiBaseUrl, apiBaseUrl));
        assertThat(connection.status()).isEqualTo(FeishuConnection.Status.CONNECTED);
        assertThat(connectionRepository.findById(connection.id()).orElseThrow().getEncryptedAppSecret())
                .doesNotContain("secret");
        assertThat(connectors.spaces(connection.id())).extracting("name").containsExactly("研发知识库");

        var source = connectors.createSource(new FeishuConnectorService.SourceInput(connection.id(),
                knowledgeBase.getId(), FeishuSource.Type.WIKI, "space-1", "研发知识库", 15));
        FeishuSyncTask first = latest(source.id());
        sync.execute(first.getId());
        awaitDocuments(knowledgeBase.getId(), 1);

        Document firstDocument = documents.findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBase.getId()).getFirst();
        assertThat(firstDocument.getName()).isEqualTo("研发规范");
        assertThat(firstDocument.getSourceType()).isEqualTo("FEISHU");
        assertThat(firstDocument.getSourceUrl()).endsWith("/wiki/wiki-node-1");
        assertThat(remoteDocuments.findBySourceId(source.id())).hasSize(1);

        stage.set(2);
        FeishuConnectorService.TaskView secondView = connectors.sync(source.id(), FeishuSyncTask.Mode.INCREMENTAL);
        sync.execute(secondView.id());
        awaitDocuments(knowledgeBase.getId(), 2);
        FeishuSyncTask second = tasks.findById(secondView.id()).orElseThrow();
        assertThat(second.getStatus()).isEqualTo(FeishuSyncTask.Status.SUCCEEDED);
        assertThat(second.getUpdatedCount()).isEqualTo(1);
        assertThat(second.getCreatedCount()).isEqualTo(1);
        assertThat(remoteDocuments.findBySourceId(source.id())).hasSize(2);
        assertThat(documents.findById(firstDocument.getId()).orElseThrow().getName()).isEqualTo("研发规范 2026");

        stage.set(3);
        FeishuConnectorService.TaskView thirdView = connectors.sync(source.id(), FeishuSyncTask.Mode.INCREMENTAL);
        sync.execute(thirdView.id());
        FeishuSyncTask third = tasks.findById(thirdView.id()).orElseThrow();
        assertThat(third.getDeletedCount()).isEqualTo(1);
        assertThat(documents.findById(firstDocument.getId())).isEmpty();
        assertThat(remoteDocuments.findBySourceId(source.id()))
                .extracting("remoteToken").containsExactly("doc-2");
    }

    private FeishuSyncTask latest(java.util.UUID sourceId) {
        return tasks.findTopBySourceIdOrderByCreatedAtDesc(sourceId).orElseThrow();
    }

    private void awaitDocuments(java.util.UUID knowledgeBaseId, int expected) throws InterruptedException {
        for (int i = 0; i < 150; i++) {
            List<Document> found = documents.findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId);
            if (found.size() == expected && found.stream().allMatch(item -> item.getStatus() == Document.Status.READY)) return;
            Thread.sleep(20);
        }
        throw new AssertionError("飞书文档未在预期时间内完成索引");
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/auth/v3/tenant_access_token/internal") && throttleAuthentication.getAndSet(false)) {
            respond(exchange, 429, "{\"code\":99991400,\"msg\":\"rate limit\"}");
            return;
        }
        if (path.endsWith("/auth/v3/tenant_access_token/internal")) {
            respond(exchange, 200, "{\"code\":0,\"tenant_access_token\":\"tenant-token\",\"expire\":7200}");
            return;
        }
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer tenant-token");
        if (path.endsWith("/wiki/v2/spaces")) {
            respond(exchange, 200, "{\"code\":0,\"data\":{\"items\":[{\"space_id\":\"space-1\",\"name\":\"研发知识库\"}],\"has_more\":false}}");
            return;
        }
        if (path.endsWith("/wiki/v2/spaces/space-1/nodes")) {
            respond(exchange, 200, nodes());
            return;
        }
        if (path.endsWith("/docx/v1/documents/doc-1/raw_content")) {
            respond(exchange, 200, "{\"code\":0,\"data\":{\"content\":\"" +
                    (stage.get() == 1 ? "合并前必须完成代码审查。" : "合并前必须完成代码审查和安全扫描。") + "\"}}");
            return;
        }
        if (path.endsWith("/docx/v1/documents/doc-2/raw_content")) {
            respond(exchange, 200, "{\"code\":0,\"data\":{\"content\":\"发布流程需要灰度验证。\"}}");
            return;
        }
        if (path.endsWith("/docx/v1/documents/doc-1")) {
            String title = stage.get() == 1 ? "研发规范" : "研发规范 2026";
            int revision = stage.get() == 1 ? 1 : 2;
            respond(exchange, 200, document(title, revision));
            return;
        }
        if (path.endsWith("/docx/v1/documents/doc-2")) {
            respond(exchange, 200, document("发布流程", 1));
            return;
        }
        respond(exchange, 404, "{\"code\":1770002,\"msg\":\"not found\"}");
    }

    private String nodes() {
        String doc2 = stage.get() >= 2
                ? ",{\"node_token\":\"wiki-node-2\",\"obj_token\":\"doc-2\",\"parent_node_token\":\"root\",\"obj_type\":\"docx\",\"title\":\"发布流程\",\"owner\":\"ou_2\",\"obj_edit_time\":\"" + Instant.now().getEpochSecond() + "\",\"has_child\":false}"
                : "";
        String doc1 = stage.get() < 3
                ? "{\"node_token\":\"wiki-node-1\",\"obj_token\":\"doc-1\",\"parent_node_token\":\"" +
                (stage.get() == 1 ? "root" : "moved-parent") + "\",\"obj_type\":\"docx\",\"title\":\"研发规范\",\"owner\":\"ou_1\",\"obj_edit_time\":\"" + Instant.now().getEpochSecond() + "\",\"has_child\":false}"
                : "";
        String separator = !doc1.isBlank() && !doc2.isBlank() ? "" : doc1.isBlank() && doc2.startsWith(",") ? "" : "";
        String items = doc1 + doc2;
        if (items.startsWith(",")) items = items.substring(1);
        return "{\"code\":0,\"data\":{\"items\":[" + items + "],\"has_more\":false}}";
    }

    private String document(String title, int revision) {
        return "{\"code\":0,\"data\":{\"document\":{\"document_id\":\"doc\",\"revision_id\":"
                + revision + ",\"title\":\"" + title + "\"}}}";
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
