package ai.jadebase;

import ai.jadebase.conversation.domain.ConversationService;
import ai.jadebase.knowledge.domain.Document;
import ai.jadebase.knowledge.domain.KnowledgeBase;
import ai.jadebase.knowledge.domain.KnowledgeBaseService;
import ai.jadebase.knowledge.infra.DocumentRepository;
import ai.jadebase.rag.application.ChatService;
import ai.jadebase.notification.domain.NotificationService;
import ai.jadebase.workspace.domain.WorkspaceSettings;
import ai.jadebase.workspace.domain.WorkspaceSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jadebase-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class JadeBaseIntegrationTest {

    @Autowired
    KnowledgeBaseService knowledgeBaseService;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    ConversationService conversationService;

    @Autowired
    WorkspaceSettingsService workspaceSettingsService;

    @Autowired
    NotificationService notificationService;

    @Test
    void indexesDocumentAndReturnsTraceableAnswerWithoutModelKey() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseService.create("研发规范", "测试知识库");
        MockMultipartFile file = new MockMultipartFile("file", "review.md", "text/markdown",
                "代码合并前必须通过单元测试和安全扫描。高风险变更需要两名审查者批准。"
                        .getBytes(StandardCharsets.UTF_8));

        Document queued = knowledgeBaseService.upload(knowledgeBase.getId(), file);
        Document document = awaitDocument(queued.getId(), Document.Status.READY, 1);
        ChatService.ChatResult result = chatService.ask(knowledgeBase.getId(), "高风险变更如何审批？");

        assertThat(document.getStatus()).isEqualTo(Document.Status.READY);
        assertThat(document.getChunkCount()).isEqualTo(1);
        assertThat(result.mode()).isEqualTo("local-demo");
        assertThat(result.sources()).isNotEmpty();
        assertThat(result.sources().getFirst().documentName()).isEqualTo("review.md");
        assertThat(result.answer()).contains("两名审查者");
        ConversationService.ConversationDetail conversation = conversationService.get(result.conversationId());
        assertThat(conversation.messages()).hasSize(2);
        assertThat(conversation.messages().getLast().sources()).isNotEmpty();
        assertThat(conversation.messages().getLast().sources().getFirst().documentName()).isEqualTo("review.md");
    }

    @Test
    void runsDocumentIndexingAsynchronouslyAndRetriesFailures() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseService.create("异步索引", "Phase 2 测试");
        MockMultipartFile unsupported = new MockMultipartFile("file", "archive.bin",
                "application/octet-stream", new byte[]{1, 2, 3});

        Document queued = knowledgeBaseService.upload(knowledgeBase.getId(), unsupported);
        assertThat(queued.getStatus()).isIn(Document.Status.QUEUED, Document.Status.PROCESSING);
        Document failed = awaitDocument(queued.getId(), Document.Status.FAILED, 1);
        assertThat(failed.getErrorMessage()).contains("暂不支持该文件类型");

        knowledgeBaseService.retryDocument(knowledgeBase.getId(), queued.getId());
        Document retried = awaitDocument(queued.getId(), Document.Status.FAILED, 2);
        assertThat(retried.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void persistsWorkspaceSettingsAndNotificationReadState() {
        workspaceSettingsService.update("Lewis", "Java 后端工程师", WorkspaceSettings.ColorMode.DARK,
                WorkspaceSettings.ChatBackground.GRAPHITE, WorkspaceSettings.Language.ZH_CN, 8, false);

        WorkspaceSettings settings = workspaceSettingsService.get();
        assertThat(settings.getProfileName()).isEqualTo("Lewis");
        assertThat(settings.getTopK()).isEqualTo(8);
        assertThat(settings.isShowCitations()).isFalse();

        assertThat(notificationService.unreadCount()).isPositive();
        notificationService.markAllRead();
        assertThat(notificationService.unreadCount()).isZero();
    }

    private Document awaitDocument(UUID documentId, Document.Status status, int attempts) throws InterruptedException {
        for (int i = 0; i < 120; i++) {
            Document document = documentRepository.findById(documentId).orElseThrow();
            if (document.getStatus() == status && document.getAttemptCount() >= attempts) return document;
            Thread.sleep(25);
        }
        throw new AssertionError("文档索引状态未在预期时间内变为 " + status);
    }
}
