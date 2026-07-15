package ai.jadebase.rag;

import ai.jadebase.rag.infra.LocalHashingEmbeddingClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHashingEmbeddingClientTest {

    private final LocalHashingEmbeddingClient client = new LocalHashingEmbeddingClient();

    @Test
    void returnsNormalizedStableVector() {
        double[] first = client.embed("企业知识库支持中文搜索");
        double[] second = client.embed("企业知识库支持中文搜索");

        assertThat(first).hasSize(384).containsExactly(second);
        double norm = Math.sqrt(java.util.Arrays.stream(first).map(value -> value * value).sum());
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.000001));
    }
}
