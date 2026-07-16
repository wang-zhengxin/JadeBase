package ai.jadebase.rag.infra;

import ai.jadebase.knowledge.domain.Chunk;
import ai.jadebase.knowledge.infra.ChunkRepository;
import ai.jadebase.rag.application.VectorCodec;
import ai.jadebase.rag.domain.SearchCandidate;
import ai.jadebase.rag.domain.VectorSearchStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "jadebase.retrieval.search-store", havingValue = "local", matchIfMissing = true)
public class LocalVectorSearchStore implements VectorSearchStore {

    private final ChunkRepository chunks;
    private final VectorCodec vectors;

    public LocalVectorSearchStore(ChunkRepository chunks, VectorCodec vectors) {
        this.chunks = chunks;
        this.vectors = vectors;
    }

    @Override
    public List<SearchCandidate> search(UUID knowledgeBaseId, double[] queryVector, int limit) {
        return chunks.findByKnowledgeBaseId(knowledgeBaseId).stream()
                .map(chunk -> candidate(chunk, cosine(queryVector, vectors.decode(chunk.getEmbedding()))))
                .filter(candidate -> candidate.score() > 0.01)
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(limit)
                .toList();
    }

    private SearchCandidate candidate(Chunk chunk, double score) {
        return new SearchCandidate(chunk.getId(), chunk.getDocumentId(), chunk.getDocumentName(),
                chunk.getChunkIndex(), chunk.getContent(), Math.max(0, score));
    }

    static double cosine(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) return 0;
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
