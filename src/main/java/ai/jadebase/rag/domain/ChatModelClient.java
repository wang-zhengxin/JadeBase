package ai.jadebase.rag.domain;

import java.util.List;

public interface ChatModelClient {

    String answer(String question, List<RetrievedChunk> context, String language);

    boolean configured();
}
