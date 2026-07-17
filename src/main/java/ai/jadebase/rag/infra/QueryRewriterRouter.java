package ai.jadebase.rag.infra;

import ai.jadebase.knowledge.domain.IndexSettingsService;
import ai.jadebase.model.ModelRuntimeResolver;
import ai.jadebase.rag.domain.QueryRewriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class QueryRewriterRouter implements QueryRewriter {

    private final ModelRuntimeResolver models;
    private final IndexSettingsService indexSettings;
    private final RestClient restClient;
    private final Counter fallbackCounter;

    public QueryRewriterRouter(ModelRuntimeResolver models, IndexSettingsService indexSettings,
                               RestClient.Builder builder, MeterRegistry meters) {
        this.models = models;
        this.indexSettings = indexSettings;
        this.restClient = builder.build();
        this.fallbackCounter = meters.counter("jadebase.query_rewrite.fallback");
    }

    @Override
    public String rewrite(String question, List<Turn> context) {
        ModelRuntimeResolver.RuntimeModel model = models.current();
        if (!indexSettings.get().isQueryRewriteEnabled() || !model.configured()
                || context == null || context.isEmpty()) {
            return question;
        }
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content",
                    "将用户最后的问题结合对话历史改写为独立、明确的知识库检索问题。只输出改写后的问题，不回答。"));
            context.stream().limit(6).forEach(turn -> messages.add(Map.of(
                    "role", "assistant".equalsIgnoreCase(turn.role()) ? "assistant" : "user",
                    "content", turn.content())));
            messages.add(Map.of("role", "user", "content", question));
            Map<String, Object> body = Map.of("model", model.modelId(), "temperature", 0.0,
                    "messages", messages);
            OpenAiCompatibleClient.ChatResponse response = restClient.post()
                    .uri(normalize(model.baseUrl()) + "/chat/completions")
                    .headers(headers -> {
                        if (model.apiKey() != null && !model.apiKey().isBlank()) headers.setBearerAuth(model.apiKey());
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OpenAiCompatibleClient.ChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) return question;
            String rewritten = response.choices().getFirst().message().content();
            return rewritten == null || rewritten.isBlank() ? question : rewritten.trim();
        } catch (RuntimeException exception) {
            fallbackCounter.increment();
            return question;
        }
    }

    private String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
