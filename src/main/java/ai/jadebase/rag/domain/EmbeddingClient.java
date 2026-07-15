package ai.jadebase.rag.domain;

public interface EmbeddingClient {
    double[] embed(String text);
}
