package ai.jadebase.rag.domain;

import java.util.List;

public interface ChatModelClient {

    Completion answer(String question, List<RetrievedChunk> context, String language,
                      Preferences preferences, boolean thinkMode);

    boolean configured();

    String modelName();

    record Completion(String answer, String reasoning) {
        public Completion {
            answer = answer == null ? "" : answer.trim();
            reasoning = reasoning == null || reasoning.isBlank() ? null : reasoning.trim();
        }
    }

    record Preferences(String personalInstructions, List<String> memories) {
        public Preferences {
            personalInstructions = personalInstructions == null ? "" : personalInstructions.trim();
            memories = memories == null ? List.of() : List.copyOf(memories);
        }
    }
}
