package ai.jadebase.common;

import ai.jadebase.knowledge.domain.KnowledgeBase;
import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapData implements CommandLineRunner {

    private final KnowledgeBaseRepository knowledgeBases;

    public BootstrapData(KnowledgeBaseRepository knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public void run(String... args) {
        if (knowledgeBases.count() == 0) {
            knowledgeBases.save(new KnowledgeBase("产品知识库", "上传产品文档，然后开始提问"));
        }
    }
}
