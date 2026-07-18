package ai.jadebase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jadebase-agent-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "jadebase.security.credential-encryption-key=agent-registry-test-key",
        "jadebase.model.api-key="
})
@AutoConfigureMockMvc
class AgentRegistryIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void managesVersionedAgentsAndExecutesPublishedConfiguration() throws Exception {
        Cookie owner = register("agent-owner@jadebase.local");
        Cookie member = register("agent-member@jadebase.local");

        String knowledgeBaseId = json.readTree(mockMvc.perform(post("/api/v1/knowledge-bases")
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Agent 研发知识库\",\"description\":\"Agent 测试\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();

        String input = """
                {"name":"Java 代码审查助手","description":"检查 Java 与 Spring 代码风险",
                 "systemPrompt":"你是资深 Java 代码审查工程师，结论必须给出证据。",
                 "knowledgeBaseId":"%s","accessLevel":"EVERYONE","thinkMode":true,"maxIterations":4}
                """.formatted(knowledgeBaseId);

        mockMvc.perform(post("/api/v1/admin/agents").cookie(member)
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/v1/admin/agents").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.currentVersion").value(0))
                .andExpect(jsonPath("$.knowledgeBaseName").value("Agent 研发知识库"))
                .andReturn();
        String agentId = json.readTree(created.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(get("/api/v1/agents").cookie(member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/v1/admin/agents/{id}/publish", agentId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.currentVersion").value(1));

        mockMvc.perform(get("/api/v1/agents").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(agentId))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].thinkMode").value(true));

        mockMvc.perform(post("/api/v1/chat").cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledgeBaseId":"%s","agentId":"%s","question":"检查控制器设计","thinkMode":false}
                                """.formatted(knowledgeBaseId, agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(agentId))
                .andExpect(jsonPath("$.agentName").value("Java 代码审查助手"))
                .andExpect(jsonPath("$.agentVersion").value(1))
                .andExpect(jsonPath("$.thinkMode").value(true));

        mockMvc.perform(get("/api/v1/admin/agents/{id}/runs", agentId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[0].version").value(1));

        JsonNode changed = json.readTree(input);
        ((com.fasterxml.jackson.databind.node.ObjectNode) changed).put("systemPrompt", "使用严格的风险等级和行级证据输出审查结论。");
        mockMvc.perform(put("/api/v1/admin/agents/{id}", agentId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(true))
                .andExpect(jsonPath("$.currentVersion").value(1));

        mockMvc.perform(post("/api/v1/admin/agents/{id}/publish", agentId).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentVersion").value(2));
        mockMvc.perform(get("/api/v1/admin/agents/{id}/versions", agentId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2));

        mockMvc.perform(patch("/api/v1/admin/agents/{id}/enabled", agentId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(get("/api/v1/agents").cookie(member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/v1/admin/agents/{id}", agentId).cookie(owner))
                .andExpect(status().isNoContent());
    }

    private Cookie register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"agent-test-pass-2026\"}".formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        return result.getResponse().getCookie("JADEBASE_SESSION");
    }
}
