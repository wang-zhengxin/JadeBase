package ai.jadebase.rag.application;

import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import ai.jadebase.rag.domain.ChatModelClient;
import ai.jadebase.rag.domain.RetrievedChunk;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final KnowledgeBaseRepository knowledgeBases;
    private final HybridRetriever retriever;
    private final ChatModelClient chatModel;

    public ChatService(KnowledgeBaseRepository knowledgeBases, HybridRetriever retriever,
                       ChatModelClient chatModel) {
        this.knowledgeBases = knowledgeBases;
        this.retriever = retriever;
        this.chatModel = chatModel;
    }

    public ChatResult ask(UUID knowledgeBaseId, String question) {
        if (!knowledgeBases.existsById(knowledgeBaseId)) throw new EntityNotFoundException("知识库不存在");
        if (question == null || question.isBlank()) throw new IllegalArgumentException("问题不能为空");
        List<RetrievedChunk> context = retriever.retrieve(knowledgeBaseId, question.trim());
        String answer = chatModel.answer(question.trim(), context);
        List<Source> sources = context.stream()
                .map(item -> new Source(item.documentId(), item.documentName(), item.chunkIndex(),
                        snippet(item.content()), Math.round(item.score() * 1000) / 1000.0))
                .toList();
        return new ChatResult(answer, chatModel.configured() ? "model" : "local-demo", sources);
    }

    private String snippet(String content) {
        return content.length() <= 240 ? content : content.substring(0, 240) + "...";
    }

    public record ChatResult(String answer, String mode, List<Source> sources) { }
    public record Source(UUID documentId, String documentName, int chunkIndex, String snippet, double score) { }
}
