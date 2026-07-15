package ai.jadebase.rag.domain;

import java.util.UUID;

public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String documentName,
        int chunkIndex,
        String content,
        double score
) {
}
