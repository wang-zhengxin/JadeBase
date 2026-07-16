package ai.jadebase.rag.infra;

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

    private final ModelProperties properties;
    private final RestClient restClient;
    private final Counter fallbackCounter;

    public RerankerRouter(ModelProperties properties, RestClient.Builder builder, MeterRegistry meters) {
        this.properties = properties;
        this.restClient = builder.build();
        this.fallbackCounter = meters.counter("jadebase.reranker.fallback");
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int limit) {
        if (!configured() || candidates.isEmpty()) return candidates.stream().limit(limit).toList();
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.rerankerModel(),
                    "query", query,
                    "documents", candidates.stream().map(RetrievedChunk::content).toList(),
                    "top_n", Math.min(limit, candidates.size()),
                    "return_documents", false);
            RerankResponse response = restClient.post()
                    .uri(normalize(properties.rerankerBaseUrl()) + "/v1/rerank")
                    .header("Authorization", "Bearer " + properties.rerankerApiKey())
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
        return properties.hasReranker();
    }

    private String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record RerankResponse(List<RerankResult> results) { }
    public record RerankResult(int index, double relevance_score) { }
}
