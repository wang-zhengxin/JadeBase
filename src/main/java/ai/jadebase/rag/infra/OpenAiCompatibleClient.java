package ai.jadebase.rag.infra;

import ai.jadebase.model.ModelRuntimeResolver;
import ai.jadebase.rag.domain.ChatModelClient;
import ai.jadebase.rag.domain.RetrievedChunk;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpenAiCompatibleClient implements ChatModelClient {

    private final ModelRuntimeResolver models;
    private final RestClient restClient;

    public OpenAiCompatibleClient(ModelRuntimeResolver models, RestClient.Builder builder) {
        this.models = models;
        this.restClient = builder.build();
    }

    @Override
    public Completion answer(String question, List<RetrievedChunk> context, String language,
                             Preferences preferences, boolean thinkMode) {
        boolean english = "en".equalsIgnoreCase(language);
        ModelRuntimeResolver.RuntimeModel model = models.current();
        if (!model.configured()) return new Completion(fallbackAnswer(context, english), null);

        StringBuilder sources = new StringBuilder();
        for (int i = 0; i < context.size(); i++) {
            RetrievedChunk chunk = context.get(i);
            sources.append("[资料").append(i + 1).append("] ")
                    .append(chunk.documentName()).append("\n")
                    .append(chunk.content()).append("\n\n");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.modelId());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt(english, preferences, thinkMode)),
                Map.of("role", "user", "content", "问题：" + question + "\n\n可用资料：\n" + sources)));
        configureThinking(body, model.modelId(), thinkMode);

        JsonNode response = restClient.post()
                .uri(normalize(model.baseUrl()) + "/chat/completions")
                .headers(headers -> {
                    if (model.apiKey() != null && !model.apiKey().isBlank()) headers.setBearerAuth(model.apiKey());
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode message = response == null ? null : response.path("choices").path(0).path("message");
        if (message == null || message.isMissingNode()) {
            throw new IllegalStateException("模型没有返回有效回答");
        }
        String answer = message.path("content").asText("").trim();
        if (answer.isBlank()) throw new IllegalStateException("模型没有返回有效回答");
        return new Completion(answer, thinkMode ? reasoning(message) : null);
    }

    @Override
    public boolean configured() {
        return models.current().configured();
    }

    @Override
    public String modelName() {
        return models.current().modelId();
    }

    private String fallbackAnswer(List<RetrievedChunk> context, boolean english) {
        if (context.isEmpty()) return english
                ? "No sufficiently relevant content was found in this knowledge base."
                : "知识库中没有找到足够相关的内容。";
        StringBuilder answer = new StringBuilder(english
                ? "No model is configured. Here is the most relevant knowledge-base content:\n\n"
                : "当前未配置大模型，以下是知识库中最相关的内容：\n\n");
        for (int i = 0; i < Math.min(3, context.size()); i++) {
            answer.append("[资料").append(i + 1).append("] ")
                    .append(context.get(i).content()).append("\n\n");
        }
        return answer.toString().trim();
    }

    private String systemPrompt(boolean english, Preferences preferences, boolean thinkMode) {
        String base = english
                ? "You are an enterprise knowledge assistant. Answer only from the supplied sources, say when evidence is insufficient, and cite facts using [资料N]. Respond in English."
                : "你是企业知识助手。只依据提供的资料回答；资料不足时明确说明。引用事实时使用[资料N]标记。使用简体中文回答。";
        StringBuilder prompt = new StringBuilder(base);
        if (thinkMode) {
            prompt.append(english
                    ? "\nUse deeper analysis before answering. If the API supports a separate reasoning field, place the concise reasoning summary there."
                    : "\n回答前进行更深入的分析；若接口支持独立推理字段，请在其中给出简洁、可审计的思考摘要。");
        }
        if (!preferences.personalInstructions().isBlank()) {
            prompt.append(english ? "\n\nUser preferences:\n" : "\n\n用户偏好：\n")
                    .append(preferences.personalInstructions());
        }
        if (!preferences.memories().isEmpty()) {
            prompt.append(english ? "\n\nStored memories:\n" : "\n\n已保存记忆：\n");
            for (int i = 0; i < preferences.memories().size(); i++) {
                prompt.append(i + 1).append(". ").append(preferences.memories().get(i)).append('\n');
            }
        }
        return prompt.toString();
    }

    private void configureThinking(Map<String, Object> body, String modelId, boolean thinkMode) {
        if (!thinkMode || modelId == null) return;
        String normalized = modelId.toLowerCase();
        if (normalized.contains("qwen3") || normalized.contains("qwq")) body.put("enable_thinking", true);
        if (normalized.matches(".*(?:^|[/_-])(o[134]|gpt-5).*")) body.put("reasoning_effort", "medium");
    }

    private String reasoning(JsonNode message) {
        for (String field : List.of("reasoning_content", "reasoning")) {
            JsonNode value = message.path(field);
            if (value.isTextual() && !value.asText().isBlank()) return value.asText();
        }
        JsonNode details = message.path("reasoning_details");
        if (details.isArray()) {
            StringBuilder result = new StringBuilder();
            details.forEach(item -> {
                String text = item.path("text").asText(item.path("content").asText(""));
                if (!text.isBlank()) result.append(text).append('\n');
            });
            if (!result.isEmpty()) return result.toString().trim();
        }
        return null;
    }

    private String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record ChatResponse(List<Choice> choices) { }
    public record Choice(Message message) { }
    public record Message(String role, String content) { }
}
