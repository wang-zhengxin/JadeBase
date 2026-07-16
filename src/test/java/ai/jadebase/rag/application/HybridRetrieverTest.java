package ai.jadebase.rag.application;

import ai.jadebase.rag.domain.KeywordSearchStore;
import ai.jadebase.rag.domain.Reranker;
import ai.jadebase.rag.domain.RetrievedChunk;
import ai.jadebase.rag.domain.SearchCandidate;
import ai.jadebase.rag.domain.VectorSearchStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRetrieverTest {

    @Test
    void mergesVectorAndBm25CandidatesWithReciprocalRankFusion() {
        UUID shared = UUID.randomUUID();
        SearchCandidate common = candidate(shared, "共同结果");
        VectorSearchStore vectors = (knowledgeBaseId, queryVector, limit) -> List.of(
                common, candidate(UUID.randomUUID(), "向量结果"));
        KeywordSearchStore keywords = (knowledgeBaseId, query, limit) -> List.of(
                common, candidate(UUID.randomUUID(), "关键词结果"));
        Reranker passthrough = new Reranker() {
            @Override
            public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int limit) {
                return candidates.stream().limit(limit).toList();
            }

            @Override
            public boolean configured() { return false; }
        };
        HybridRetriever retriever = new HybridRetriever(vectors, keywords, text -> new double[]{1},
                passthrough, new RetrievalProperties(6, 30, 60, "local", true, true),
                new SimpleMeterRegistry());

        HybridRetriever.RetrievalResult result = retriever.retrieveWithDiagnostics(UUID.randomUUID(), "查询", 3);

        assertThat(result.chunks()).hasSize(3);
        assertThat(result.chunks().getFirst().chunkId()).isEqualTo(shared);
        assertThat(result.diagnostics().vectorCandidates()).isEqualTo(2);
        assertThat(result.diagnostics().keywordCandidates()).isEqualTo(2);
        assertThat(result.diagnostics().reranked()).isFalse();
    }

    private SearchCandidate candidate(UUID id, String content) {
        return new SearchCandidate(id, UUID.randomUUID(), "guide.md", 0, content, 1);
    }
}
