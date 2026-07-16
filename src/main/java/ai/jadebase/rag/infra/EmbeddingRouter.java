package ai.jadebase.rag.infra;

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

    private final ModelProperties properties;
    private final LocalHashingEmbeddingClient local;
    private final RestClient restClient;

    public EmbeddingRouter(ModelProperties properties, LocalHashingEmbeddingClient local,
                           RestClient.Builder builder) {
        this.properties = properties;
        this.local = local;
        this.restClient = builder.build();
    }

    @Override
    public double[] embed(String text) {
        if (!properties.hasEmbeddingModel()) return local.embed(text);
        EmbeddingResponse response = restClient.post()
                .uri(normalize(properties.embeddingBaseUrl()) + "/v1/embeddings")
                .header("Authorization", "Bearer " + properties.embeddingApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", properties.embeddingModel(), "input", text,
                        "dimensions", properties.embeddingDimensions()))
                .retrieve()
                .body(EmbeddingResponse.class);
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Embedding 模型没有返回有效向量");
        }
        List<Double> values = response.data().getFirst().embedding();
        if (values.size() != properties.embeddingDimensions()) {
            throw new IllegalStateException("Embedding 维度不匹配：期望 " + properties.embeddingDimensions()
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
