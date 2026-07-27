package ai.jadebase.rag.infra;

import ai.jadebase.model.LanguageModel;
import ai.jadebase.model.ModelRuntimeResolver;
import ai.jadebase.rag.domain.Reranker;
import ai.jadebase.rag.domain.RetrievedChunk;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class RerankerRouter implements Reranker {

    private final ModelRuntimeResolver models;
    private final RestClient restClient;
    private final Counter fallbackCounter;

    public RerankerRouter(ModelRuntimeResolver models, RestClient.Builder builder, MeterRegistry meters) {
        this.models = models;
        this.restClient = builder.build();
        this.fallbackCounter = meters.counter("jadebase.reranker.fallback");
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int limit) {
        if (!configured() || candidates.isEmpty()) return candidates.stream().limit(limit).toList();
        try {
            ModelRuntimeResolver.RuntimeModel model = models.current(LanguageModel.Capability.RERANKER);
            Map<String, Object> body = Map.of(
                    "model", model.modelId(),
                    "query", query,
                    "documents", candidates.stream().map(RetrievedChunk::content).toList(),
                    "top_n", Math.min(limit, candidates.size()),
                    "return_documents", false);
            RerankResponse response = restClient.post()
                    .uri(normalize(model.baseUrl()) + "/rerank")
                    .headers(headers -> {
                        if (model.apiKey() != null && !model.apiKey().isBlank()) headers.setBearerAuth(model.apiKey());
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(RerankResponse.class);
            if (response == null || response.results() == null) throw new IllegalStateException("Reranker 没有返回结果");
            List<RetrievedChunk> ranked = new ArrayList<>();
            response.results().stream()
                    .sorted(Comparator.comparingDouble(RerankResult::relevance_score).reversed())
                    .limit(limit)
                    .forEach(result -> {
                        if (result.index() >= 0 && result.index() < candidates.size()) {
                            RetrievedChunk source = candidates.get(result.index());
                            ranked.add(new RetrievedChunk(source.chunkId(), source.documentId(), source.documentName(),
                                    source.chunkIndex(), source.content(), result.relevance_score()));
                        }
                    });
            return ranked.isEmpty() ? candidates.stream().limit(limit).toList() : ranked;
        } catch (RuntimeException exception) {
            fallbackCounter.increment();
            return candidates.stream().limit(limit).toList();
        }
    }

    @Override
    public boolean configured() {
        return models.current(LanguageModel.Capability.RERANKER).configured();
    }

    private String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record RerankResponse(List<RerankResult> results) { }
    public record RerankResult(int index, double relevance_score) { }
}
