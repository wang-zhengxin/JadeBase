package ai.jadebase.rag.infra;

import ai.jadebase.rag.domain.ChatModelClient;
import ai.jadebase.rag.domain.RetrievedChunk;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleClient implements ChatModelClient {

    private final ModelProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleClient(ModelProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public String answer(String question, List<RetrievedChunk> context, String language) {
        boolean english = "en".equalsIgnoreCase(language);
        if (!configured()) return fallbackAnswer(context, english);

        StringBuilder sources = new StringBuilder();
        for (int i = 0; i < context.size(); i++) {
            RetrievedChunk chunk = context.get(i);
            sources.append("[资料").append(i + 1).append("] ")
                    .append(chunk.documentName()).append("\n")
                    .append(chunk.content()).append("\n\n");
        }
        Map<String, Object> body = Map.of(
                "model", properties.chatModel(),
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt(english)),
                        Map.of("role", "user", "content", "问题：" + question + "\n\n可用资料：\n" + sources)));

        ChatResponse response = restClient.post()
                .uri(normalize(properties.baseUrl()) + "/v1/chat/completions")
                .header("Authorization", "Bearer " + properties.apiKey())
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
        return properties.hasChatModel();
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

    private String systemPrompt(boolean english) {
        return english
                ? "You are an enterprise knowledge assistant. Answer only from the supplied sources, say when evidence is insufficient, and cite facts using [资料N]. Respond in English."
                : "你是企业知识助手。只依据提供的资料回答；资料不足时明确说明。引用事实时使用[资料N]标记。使用简体中文回答。";
    }

    private String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record ChatResponse(List<Choice> choices) { }
    public record Choice(Message message) { }
    public record Message(String role, String content) { }
}
