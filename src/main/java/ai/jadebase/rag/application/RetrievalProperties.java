package ai.jadebase.rag.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jadebase.retrieval")
public record RetrievalProperties(int topK, int candidateK, int rrfK,
                                  String searchStore, boolean rerankEnabled, boolean queryRewriteEnabled) {
}
