package ai.jadebase.rag.domain;

import java.util.List;

public interface Reranker {
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int limit);
    boolean configured();
}
