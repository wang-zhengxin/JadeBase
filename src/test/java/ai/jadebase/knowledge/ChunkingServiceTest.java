package ai.jadebase.knowledge;

import ai.jadebase.knowledge.domain.ChunkingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService();

    @Test
    void keepsShortChineseDocumentInOneChunk() {
        List<String> chunks = service.split("JadeBase 是企业知识平台。它支持中文检索和引用。");

        assertThat(chunks).containsExactly("JadeBase 是企业知识平台。它支持中文检索和引用。");
    }

    @Test
    void splitsLongTextAtSentenceBoundaryWithOverlap() {
        String paragraph = "这是一个用于验证中文文档分块的完整句子。".repeat(80);

        List<String> chunks = service.split(paragraph);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allMatch(chunk -> chunk.length() <= 700);
        assertThat(chunks.get(1)).contains("中文文档分块");
    }
}
