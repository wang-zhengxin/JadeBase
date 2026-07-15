package ai.jadebase;

import ai.jadebase.knowledge.domain.Document;
import ai.jadebase.knowledge.domain.KnowledgeBase;
import ai.jadebase.knowledge.domain.KnowledgeBaseService;
import ai.jadebase.rag.application.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jadebase-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class JadeBaseIntegrationTest {

    @Autowired
    KnowledgeBaseService knowledgeBaseService;

    @Autowired
    ChatService chatService;

    @Test
    void indexesDocumentAndReturnsTraceableAnswerWithoutModelKey() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseService.create("研发规范", "测试知识库");
        MockMultipartFile file = new MockMultipartFile("file", "review.md", "text/markdown",
                "代码合并前必须通过单元测试和安全扫描。高风险变更需要两名审查者批准。"
                        .getBytes(StandardCharsets.UTF_8));

        Document document = knowledgeBaseService.upload(knowledgeBase.getId(), file);
        ChatService.ChatResult result = chatService.ask(knowledgeBase.getId(), "高风险变更如何审批？");

        assertThat(document.getStatus()).isEqualTo(Document.Status.READY);
        assertThat(document.getChunkCount()).isEqualTo(1);
        assertThat(result.mode()).isEqualTo("local-demo");
        assertThat(result.sources()).isNotEmpty();
        assertThat(result.sources().getFirst().documentName()).isEqualTo("review.md");
        assertThat(result.answer()).contains("两名审查者");
    }
}
