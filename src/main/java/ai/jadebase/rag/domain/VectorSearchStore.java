package ai.jadebase.rag.domain;

import java.util.List;
import java.util.UUID;

public interface VectorSearchStore {
    List<SearchCandidate> search(UUID knowledgeBaseId, double[] queryVector, int limit);
}
