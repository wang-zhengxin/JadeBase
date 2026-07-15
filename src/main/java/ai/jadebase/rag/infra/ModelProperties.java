package ai.jadebase.rag.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jadebase.model")
public record ModelProperties(
        String baseUrl,
        String apiKey,
        String chatModel,
        String embeddingBaseUrl,
        String embeddingApiKey,
        String embeddingModel
) {
    public boolean hasChatModel() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasEmbeddingModel() {
        return embeddingApiKey != null && !embeddingApiKey.isBlank();
    }
}
