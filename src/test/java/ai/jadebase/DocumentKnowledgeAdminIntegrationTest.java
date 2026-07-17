package ai.jadebase;

import ai.jadebase.knowledge.domain.Document;
import ai.jadebase.knowledge.domain.KnowledgeBase;
import ai.jadebase.knowledge.domain.KnowledgeBaseService;
import ai.jadebase.knowledge.infra.DocumentRepository;
import ai.jadebase.workspace.domain.WorkspaceSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jadebase-document-admin-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "jadebase.indexing.poll-delay-ms=25",
        "jadebase.indexing.recovery-delay-ms=100"
})
@AutoConfigureMockMvc
class DocumentKnowledgeAdminIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired KnowledgeBaseService knowledgeBases;
    @Autowired DocumentRepository documents;
    @Autowired WorkspaceSettingsService workspaceSettings;

    @Test
    void managesDocumentSetsAndRuntimeIndexSettingsAsOwner() throws Exception {
        Cookie owner = register("document-owner@jadebase.local");
        Cookie member = register("document-member@jadebase.local");
        KnowledgeBase knowledgeBase = knowledgeBases.create("管理资料", "文档集测试");
        MockMultipartFile file = new MockMultipartFile("file", "handbook.md", "text/markdown",
                "JadeBase 将企业文档组织为可检索的知识。".repeat(80).getBytes(StandardCharsets.UTF_8));
        Document document = knowledgeBases.upload(knowledgeBase.getId(), file);
        awaitReady(document);

        mockMvc.perform(get("/api/v1/admin/knowledge/summary").cookie(member))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/knowledge/summary").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knowledgeBaseCount").isNumber())
                .andExpect(jsonPath("$.documentCount").value(1))
                .andExpect(jsonPath("$.readyCount").value(1));

        MvcResult created = mockMvc.perform(post("/api/v1/admin/knowledge/document-sets")
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"新人必读","description":"入职资料","documentIds":["%s"]}
                                """.formatted(document.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentCount").value(1))
                .andExpect(jsonPath("$.readyCount").value(1))
                .andExpect(jsonPath("$.documents[0].name").value("handbook.md"))
                .andReturn();
        JsonNode set = json.readTree(created.getResponse().getContentAsString());

        mockMvc.perform(put("/api/v1/admin/knowledge/document-sets/{id}", set.path("id").asText())
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"公开资料","description":"","documentIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("公开资料"))
                .andExpect(jsonPath("$.documentCount").value(0));

        mockMvc.perform(put("/api/v1/admin/knowledge/index-settings")
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chunkSize":900,"chunkOverlap":120,"topK":8,"candidateK":50,
                                 "rrfK":70,"rerankEnabled":false,"queryRewriteEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topK").value(8))
                .andExpect(jsonPath("$.chunkSize").value(900))
                .andExpect(jsonPath("$.reindexRequired").value(true));
        assertThat(workspaceSettings.get().getTopK()).isEqualTo(8);

        mockMvc.perform(put("/api/v1/admin/knowledge/index-settings")
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chunkSize":400,"chunkOverlap":400,"topK":6,"candidateK":40,
                                 "rrfK":60,"rerankEnabled":true,"queryRewriteEnabled":true}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/knowledge/reindex").cookie(owner))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.queuedDocuments").value(1))
                .andExpect(jsonPath("$.reindexRequired").value(false));

        mockMvc.perform(delete("/api/v1/admin/knowledge/document-sets/{id}", set.path("id").asText()).cookie(owner))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/knowledge/document-sets").cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    private Cookie register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"test-pass-2026"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        return result.getResponse().getCookie("JADEBASE_SESSION");
    }

    private void awaitReady(Document document) throws InterruptedException {
        for (int i = 0; i < 120; i++) {
            Document current = documents.findById(document.getId()).orElseThrow();
            if (current.getStatus() == Document.Status.READY) return;
            Thread.sleep(25);
        }
        throw new AssertionError("文档未在预期时间内完成索引");
    }
}
