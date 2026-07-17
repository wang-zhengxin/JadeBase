package ai.jadebase;

import ai.jadebase.model.ModelProvider;
import ai.jadebase.model.ModelProviderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jadebase-model-admin-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "jadebase.security.credential-encryption-key=model-admin-test-key",
        "jadebase.model.api-key="
})
@AutoConfigureMockMvc
class LanguageModelAdminIntegrationTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final AtomicReference<String> lastModel = new AtomicReference<>();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired ModelProviderRepository providers;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> respond(exchange, 200, """
                {"object":"list","data":[
                  {"id":"mock-chat","object":"model","owned_by":"test"},
                  {"id":"mock-reasoner","object":"model","owned_by":"test"}
                ]}
                """));
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String model = request.contains("mock-reasoner") ? "mock-reasoner" : "mock-chat";
            lastModel.set(model);
            respond(exchange, 200, """
                    {"id":"chatcmpl-test","choices":[{"message":{"role":"assistant","content":"mock-answer",
                     "reasoning_content":"分析问题并核对知识库证据。"}}]}
                    """);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void managesEncryptedProvidersAndUsesSelectedModelAtRuntime() throws Exception {
        Cookie owner = register("owner@jadebase.local");
        Cookie member = register("member@jadebase.local");

        mockMvc.perform(get("/api/v1/admin/model-providers/catalog").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'DEEPSEEK')]").exists())
                .andExpect(jsonPath("$[?(@.type == 'OLLAMA')]").exists());

        mockMvc.perform(get("/api/v1/admin/model-providers").cookie(member))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/model-providers/discover")
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerType":"OPENAI_COMPATIBLE","baseUrl":"%s","apiKey":"model-secret"}
                                """.formatted(baseUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models.length()").value(2));

        MvcResult created = mockMvc.perform(post("/api/v1/admin/model-providers")
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerType":"OPENAI_COMPATIBLE","displayName":"测试兼容服务",
                                 "baseUrl":"%s","apiKey":"model-secret",
                                 "modelIds":["mock-chat","mock-reasoner"]}
                                """.formatted(baseUrl)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.models[0].defaultModel").value(true))
                .andReturn();

        String providerId = json.readTree(created.getResponse().getContentAsString()).path("id").asText();
        ModelProvider stored = providers.findById(java.util.UUID.fromString(providerId)).orElseThrow();
        assertThat(stored.getEncryptedApiKey()).doesNotContain("model-secret");

        mockMvc.perform(post("/api/v1/admin/model-providers/{id}/discover", providerId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerType":"OPENAI_COMPATIBLE","baseUrl":"%s","apiKey":""}
                                """.formatted(baseUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0]").value("mock-chat"));

        mockMvc.perform(put("/api/v1/admin/model-providers/default")
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":"%s","modelId":"mock-reasoner"}
                                """.formatted(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("mock-reasoner"));

        mockMvc.perform(get("/api/v1/models/current").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("mock-reasoner"))
                .andExpect(jsonPath("$.source").value("database"));

        MvcResult knowledgeBase = mockMvc.perform(post("/api/v1/knowledge-bases")
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"模型测试库\",\"description\":\"\"}"))
                .andExpect(status().isCreated()).andReturn();
        String knowledgeBaseId = json.readTree(knowledgeBase.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(post("/api/v1/chat")
                        .cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledgeBaseId":"%s","question":"测试默认模型","thinkMode":true}
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("mock-answer"))
                .andExpect(jsonPath("$.reasoning").value("分析问题并核对知识库证据。"))
                .andExpect(jsonPath("$.thinkMode").value(true))
                .andExpect(jsonPath("$.model").value("mock-reasoner"));
        assertThat(lastModel.get()).isEqualTo("mock-reasoner");

        MvcResult stream = mockMvc.perform(post("/api/v1/chat/stream")
                        .cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledgeBaseId":"%s","question":"流式思考测试","thinkMode":true}
                                """.formatted(knowledgeBaseId)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:thinking")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:result")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"reasoning\"")));

        mockMvc.perform(put("/api/v1/admin/model-providers/{id}", providerId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerType":"OPENAI_COMPATIBLE","displayName":"更新后的服务",
                                 "baseUrl":"%s","apiKey":"","modelIds":["mock-chat"]}
                                """.formatted(baseUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("更新后的服务"))
                .andExpect(jsonPath("$.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.models[0].defaultModel").value(true));

        mockMvc.perform(delete("/api/v1/admin/model-providers/{id}", providerId).cookie(owner))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/models/current").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        mockMvc.perform(post("/api/v1/admin/model-providers")
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerType":"OLLAMA","displayName":"本地 Ollama",
                                 "baseUrl":"%s","apiKey":"","modelIds":["mock-chat"]}
                                """.formatted(baseUrl)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKeyConfigured").value(false));
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

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
