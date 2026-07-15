package ai.jadebase.rag.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jadebase.retrieval")
public record RetrievalProperties(int topK, double vectorWeight, double keywordWeight) {
}
