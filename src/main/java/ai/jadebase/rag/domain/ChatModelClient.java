package ai.jadebase.rag.domain;

import java.util.List;
import java.util.UUID;

public interface ChatModelClient {

    Completion answer(String question, List<RetrievedChunk> context, String language,
                      Preferences preferences, boolean thinkMode);

    boolean configured();

    String modelName();

    record Completion(String answer, String reasoning, String modelName, boolean configured) {
        public Completion(String answer, String reasoning) {
            this(answer, reasoning, null, false);
        }

        public Completion {
            answer = answer == null ? "" : answer.trim();
            reasoning = reasoning == null || reasoning.isBlank() ? null : reasoning.trim();
            modelName = modelName == null || modelName.isBlank() ? "本地演示" : modelName.trim();
        }
    }

    record Preferences(String personalInstructions, List<String> memories, String agentInstructions,
                       UUID modelProviderId, String modelId) {
        public Preferences(String personalInstructions, List<String> memories) {
            this(personalInstructions, memories, "", null, null);
        }

        public Preferences {
            personalInstructions = personalInstructions == null ? "" : personalInstructions.trim();
            memories = memories == null ? List.of() : List.copyOf(memories);
            agentInstructions = agentInstructions == null ? "" : agentInstructions.trim();
        }
    }
}
