package ai.jadebase.rag.infra;

import ai.jadebase.model.LanguageModel;
import ai.jadebase.model.ModelRuntimeResolver;
import ai.jadebase.rag.domain.EmbeddingClient;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Primary
@Component
public class EmbeddingRouter implements EmbeddingClient {

    private final ModelRuntimeResolver models;
    private final LocalHashingEmbeddingClient local;
    private final RestClient restClient;

    public EmbeddingRouter(ModelRuntimeResolver models, LocalHashingEmbeddingClient local,
                           RestClient.Builder builder) {
        this.models = models;
        this.local = local;
        this.restClient = builder.build();
    }

    @Override
    public double[] embed(String text) {
        ModelRuntimeResolver.RuntimeModel model = models.current(LanguageModel.Capability.EMBEDDING);
        if (!model.configured()) return local.embed(text);
        int dimensions = model.embeddingDimensions() == null ? 384 : model.embeddingDimensions();
        EmbeddingResponse response = restClient.post()
                .uri(normalize(model.baseUrl()) + "/embeddings")
                .headers(headers -> {
                    if (model.apiKey() != null && !model.apiKey().isBlank()) headers.setBearerAuth(model.apiKey());
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model.modelId(), "input", text))
                .retrieve()
                .body(EmbeddingResponse.class);
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Embedding 模型没有返回有效向量");
        }
        List<Double> values = response.data().getFirst().embedding();
        if (values.size() != dimensions) {
            throw new IllegalStateException("Embedding 维度不匹配：期望 " + dimensions
                    + "，实际 " + values.size());
        }
        double[] vector = new double[values.size()];
        for (int i = 0; i < values.size(); i++) vector[i] = values.get(i);
        return vector;
    }

    private String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record EmbeddingResponse(List<EmbeddingData> data) { }
    public record EmbeddingData(List<Double> embedding) { }
}
