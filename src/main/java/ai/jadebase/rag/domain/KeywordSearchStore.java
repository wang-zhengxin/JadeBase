package ai.jadebase.rag.domain;

import java.util.List;
import java.util.UUID;

public interface KeywordSearchStore {
    List<SearchCandidate> search(UUID knowledgeBaseId, String query, int limit);
}
