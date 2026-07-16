package ai.jadebase.rag.application;

import ai.jadebase.rag.domain.EmbeddingClient;
import ai.jadebase.rag.domain.KeywordSearchStore;
import ai.jadebase.rag.domain.Reranker;
import ai.jadebase.rag.domain.RetrievedChunk;
import ai.jadebase.rag.domain.SearchCandidate;
import ai.jadebase.rag.domain.VectorSearchStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HybridRetriever {

    private final VectorSearchStore vectors;
    private final KeywordSearchStore keywords;
    private final EmbeddingClient embeddings;
    private final Reranker reranker;
    private final RetrievalProperties properties;
    private final Timer retrievalTimer;

    public HybridRetriever(VectorSearchStore vectors, KeywordSearchStore keywords, EmbeddingClient embeddings,
                           Reranker reranker, RetrievalProperties properties, MeterRegistry meters) {
        this.vectors = vectors;
        this.keywords = keywords;
        this.embeddings = embeddings;
        this.reranker = reranker;
        this.properties = properties;
        this.retrievalTimer = meters.timer("jadebase.retrieval.duration");
    }

    public List<RetrievedChunk> retrieve(UUID knowledgeBaseId, String query) {
        return retrieve(knowledgeBaseId, query, properties.topK());
    }

    public List<RetrievedChunk> retrieve(UUID knowledgeBaseId, String query, int topK) {
        return retrieveWithDiagnostics(knowledgeBaseId, query, topK).chunks();
    }

    public RetrievalResult retrieveWithDiagnostics(UUID knowledgeBaseId, String query, int topK) {
        long started = System.nanoTime();
        int resultLimit = Math.min(12, Math.max(1, topK));
        int candidateLimit = Math.max(resultLimit, properties.candidateK());
        List<SearchCandidate> vectorCandidates = vectors.search(knowledgeBaseId, embeddings.embed(query), candidateLimit);
        List<SearchCandidate> keywordCandidates = keywords.search(knowledgeBaseId, query, candidateLimit);
        List<RetrievedChunk> fused = reciprocalRankFusion(vectorCandidates, keywordCandidates, candidateLimit);
        List<RetrievedChunk> result = properties.rerankEnabled()
                ? reranker.rerank(query, fused, resultLimit)
                : fused.stream().limit(resultLimit).toList();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        retrievalTimer.record(elapsedMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        return new RetrievalResult(result, new RetrievalDiagnostics(query, vectorCandidates.size(),
                keywordCandidates.size(), fused.size(), reranker.configured() && properties.rerankEnabled(), elapsedMillis));
    }

    List<RetrievedChunk> reciprocalRankFusion(List<SearchCandidate> vectorCandidates,
                                              List<SearchCandidate> keywordCandidates, int limit) {
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, SearchCandidate> candidates = new LinkedHashMap<>();
        accumulate(vectorCandidates, scores, candidates);
        accumulate(keywordCandidates, scores, candidates);
        return candidates.values().stream()
                .map(candidate -> candidate.retrieved(scores.get(candidate.chunkId())))
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(limit)
                .toList();
    }

    private void accumulate(List<SearchCandidate> ranked, Map<UUID, Double> scores,
                            Map<UUID, SearchCandidate> candidates) {
        for (int index = 0; index < ranked.size(); index++) {
            SearchCandidate candidate = ranked.get(index);
            candidates.putIfAbsent(candidate.chunkId(), candidate);
            scores.merge(candidate.chunkId(), 1.0 / (properties.rrfK() + index + 1), Double::sum);
        }
    }

    public record RetrievalResult(List<RetrievedChunk> chunks, RetrievalDiagnostics diagnostics) { }
    public record RetrievalDiagnostics(String query, int vectorCandidates, int keywordCandidates,
                                       int fusedCandidates, boolean reranked, long elapsedMillis) { }
}
