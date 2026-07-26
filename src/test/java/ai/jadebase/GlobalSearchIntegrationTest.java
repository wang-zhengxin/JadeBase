package ai.jadebase;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jadebase-global-search-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "jadebase.security.credential-encryption-key=global-search-test-key",
        "jadebase.model.api-key="
})
@AutoConfigureMockMvc
class GlobalSearchIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void searchesConversationsKnowledgeDocumentsAndVisibleAgents() throws Exception {
        Cookie owner = register("search-owner@jadebase.local");
        String knowledgeBaseId = json.readTree(mockMvc.perform(post("/api/v1/knowledge-bases")
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"检索平台知识库\",\"description\":\"统一搜索资料\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();

        MockMultipartFile document = new MockMultipartFile("file", "检索架构说明.md", "text/markdown",
                "JadeBase 使用混合检索。".getBytes());
        mockMvc.perform(multipart("/api/v1/knowledge-bases/{id}/documents", knowledgeBaseId)
                        .file(document).cookie(owner))
                .andExpect(status().isAccepted());

        String agentId = json.readTree(mockMvc.perform(post("/api/v1/admin/agents").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"检索评估 Agent","description":"评估知识库检索质量",
                                 "useKnowledge":false,"accessLevel":"EVERYONE"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
        mockMvc.perform(post("/api/v1/admin/agents/{id}/publish", agentId).cookie(owner))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/chat").cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledgeBaseId":"%s","question":"检索策略是什么？"}
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/search").param("query", "检索").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("检索"))
                .andExpect(jsonPath("$.conversations[0].title").value("检索策略是什么？"))
                .andExpect(jsonPath("$.knowledgeBases[0].name").value("检索平台知识库"))
                .andExpect(jsonPath("$.documents[0].name").value("检索架构说明.md"))
                .andExpect(jsonPath("$.documents[0].knowledgeBaseId").value(knowledgeBaseId))
                .andExpect(jsonPath("$.agents[0].id").value(agentId))
                .andExpect(jsonPath("$.agents[0].name").value("检索评估 Agent"));

        mockMvc.perform(get("/api/v1/search").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversations.length()").value(1))
                .andExpect(jsonPath("$.knowledgeBases[0].name").value("检索平台知识库"))
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.agents.length()").value(1));
    }

    private Cookie register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"search-test-pass-2026\"}".formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        return result.getResponse().getCookie("JADEBASE_SESSION");
    }
}
