package ai.jadebase.rag.domain;

import java.util.List;

public interface QueryRewriter {
    String rewrite(String question, List<Turn> context);

    record Turn(String role, String content) { }
}
