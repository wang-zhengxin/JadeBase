package ai.jadebase.rag.application;

import ai.jadebase.conversation.domain.ConversationService;
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
    private final ConversationService conversations;

    public ChatService(KnowledgeBaseRepository knowledgeBases, HybridRetriever retriever,
                       ChatModelClient chatModel, ConversationService conversations) {
        this.knowledgeBases = knowledgeBases;
        this.retriever = retriever;
        this.chatModel = chatModel;
        this.conversations = conversations;
    }

    public ChatResult ask(UUID knowledgeBaseId, String question) {
        return ask(knowledgeBaseId, null, question, null, null);
    }

    public ChatResult ask(UUID knowledgeBaseId, UUID conversationId, String question,
                          Integer topK, String language) {
        if (!knowledgeBases.existsById(knowledgeBaseId)) throw new EntityNotFoundException("知识库不存在");
        if (question == null || question.isBlank()) throw new IllegalArgumentException("问题不能为空");
        String normalizedQuestion = question.trim();
        int resultLimit = topK == null ? 6 : topK;
        if (resultLimit < 1 || resultLimit > 12) throw new IllegalArgumentException("召回片段数必须在 1 到 12 之间");
        String responseLanguage = "en".equalsIgnoreCase(language) ? "en" : "zh-CN";
        List<RetrievedChunk> context = retriever.retrieve(knowledgeBaseId, normalizedQuestion, resultLimit);
        String answer = chatModel.answer(normalizedQuestion, context, responseLanguage);
        List<Source> sources = context.stream()
                .map(item -> new Source(item.documentId(), item.documentName(), item.chunkIndex(),
                        snippet(item.content()), Math.round(item.score() * 1000) / 1000.0))
                .toList();
        String mode = chatModel.configured() ? "model" : "local-demo";
        UUID savedConversationId = conversations.recordExchange(knowledgeBaseId, conversationId,
                normalizedQuestion, answer, mode, sources.stream().map(source ->
                        new ConversationService.SourceSnapshot(source.documentId(), source.documentName(),
                                source.chunkIndex(), source.snippet(), source.score())).toList());
        return new ChatResult(savedConversationId, answer, mode, sources);
    }

    private String snippet(String content) {
        return content.length() <= 240 ? content : content.substring(0, 240) + "...";
    }

    public record ChatResult(UUID conversationId, String answer, String mode, List<Source> sources) { }
    public record Source(UUID documentId, String documentName, int chunkIndex, String snippet, double score) { }
}
