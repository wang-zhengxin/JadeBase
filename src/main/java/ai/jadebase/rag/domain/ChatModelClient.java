package ai.jadebase.rag.domain;

import java.util.List;

public interface ChatModelClient {

    String answer(String question, List<RetrievedChunk> context, String language, Preferences preferences);

    boolean configured();

    String modelName();

    record Preferences(String personalInstructions, List<String> memories) {
        public Preferences {
            personalInstructions = personalInstructions == null ? "" : personalInstructions.trim();
            memories = memories == null ? List.of() : List.copyOf(memories);
        }
    }
}
