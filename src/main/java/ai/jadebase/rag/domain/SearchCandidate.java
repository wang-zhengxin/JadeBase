package ai.jadebase.rag.domain;

import java.util.UUID;

public record SearchCandidate(
        UUID chunkId,
        UUID documentId,
        String documentName,
        int chunkIndex,
        String content,
        double score
) {
    public RetrievedChunk retrieved(double finalScore) {
        return new RetrievedChunk(chunkId, documentId, documentName, chunkIndex, content, finalScore);
    }
}
