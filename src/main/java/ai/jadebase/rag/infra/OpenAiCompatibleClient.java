package ai.jadebase.rag.infra;

import ai.jadebase.model.ModelRuntimeResolver;
import ai.jadebase.rag.domain.ChatModelClient;
import ai.jadebase.rag.domain.RetrievedChunk;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
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
    public String answer(String question, List<RetrievedChunk> context, String language,
                         Preferences preferences) {
        boolean english = "en".equalsIgnoreCase(language);
        ModelRuntimeResolver.RuntimeModel model = models.current();
        if (!model.configured()) return fallbackAnswer(context, english);

        StringBuilder sources = new StringBuilder();
        for (int i = 0; i < context.size(); i++) {
            RetrievedChunk chunk = context.get(i);
            sources.append("[资料").append(i + 1).append("] ")
                    .append(chunk.documentName()).append("\n")
                    .append(chunk.content()).append("\n\n");
        }
        Map<String, Object> body = Map.of(
                "model", model.modelId(),
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt(english, preferences)),
                        Map.of("role", "user", "content", "问题：" + question + "\n\n可用资料：\n" + sources)));

        ChatResponse response = restClient.post()
                .uri(normalize(model.baseUrl()) + "/chat/completions")
                .headers(headers -> {
                    if (model.apiKey() != null && !model.apiKey().isBlank()) headers.setBearerAuth(model.apiKey());
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ChatResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("模型没有返回有效回答");
        }
        return response.choices().getFirst().message().content();
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

    private String systemPrompt(boolean english, Preferences preferences) {
        String base = english
                ? "You are an enterprise knowledge assistant. Answer only from the supplied sources, say when evidence is insufficient, and cite facts using [资料N]. Respond in English."
                : "你是企业知识助手。只依据提供的资料回答；资料不足时明确说明。引用事实时使用[资料N]标记。使用简体中文回答。";
        StringBuilder prompt = new StringBuilder(base);
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

    private String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record ChatResponse(List<Choice> choices) { }
    public record Choice(Message message) { }
    public record Message(String role, String content) { }
}
