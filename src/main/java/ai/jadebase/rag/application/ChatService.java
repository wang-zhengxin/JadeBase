package ai.jadebase.rag.application;

import ai.jadebase.conversation.domain.ConversationService;
import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import ai.jadebase.rag.domain.ChatModelClient;
import ai.jadebase.rag.domain.RetrievedChunk;
import ai.jadebase.rag.domain.QueryRewriter;
import ai.jadebase.workspace.domain.WorkspaceMemoryService;
import ai.jadebase.workspace.domain.WorkspaceSettings;
import ai.jadebase.workspace.domain.WorkspaceSettingsService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.time.Duration;

@Service
public class ChatService {

    private final KnowledgeBaseRepository knowledgeBases;
    private final HybridRetriever retriever;
    private final ChatModelClient chatModel;
    private final ConversationService conversations;
    private final QueryRewriter queryRewriter;
    private final WorkspaceSettingsService workspaceSettings;
    private final WorkspaceMemoryService workspaceMemories;

    public ChatService(KnowledgeBaseRepository knowledgeBases, HybridRetriever retriever,
                       ChatModelClient chatModel, ConversationService conversations,
                       QueryRewriter queryRewriter, WorkspaceSettingsService workspaceSettings,
                       WorkspaceMemoryService workspaceMemories) {
        this.knowledgeBases = knowledgeBases;
        this.retriever = retriever;
        this.chatModel = chatModel;
        this.conversations = conversations;
        this.queryRewriter = queryRewriter;
        this.workspaceSettings = workspaceSettings;
        this.workspaceMemories = workspaceMemories;
    }

    public ChatResult ask(UUID knowledgeBaseId, String question) {
        return ask(knowledgeBaseId, null, question, null, null, false);
    }

    public ChatResult ask(UUID knowledgeBaseId, UUID conversationId, String question,
                          Integer topK, String language) {
        return ask(knowledgeBaseId, conversationId, question, topK, language, false);
    }

    public ChatResult ask(UUID knowledgeBaseId, UUID conversationId, String question,
                          Integer topK, String language, boolean thinkMode) {
        long started = System.nanoTime();
        if (!knowledgeBases.existsById(knowledgeBaseId)) throw new EntityNotFoundException("知识库不存在");
        if (question == null || question.isBlank()) throw new IllegalArgumentException("问题不能为空");
        String normalizedQuestion = question.trim();
        WorkspaceSettings settings = workspaceSettings.get();
        int resultLimit = topK == null ? settings.getTopK() : topK;
        if (resultLimit < 1 || resultLimit > 12) throw new IllegalArgumentException("召回片段数必须在 1 到 12 之间");
        String responseLanguage = language == null
                ? (settings.getLanguage() == WorkspaceSettings.Language.EN ? "en" : "zh-CN")
                : ("en".equalsIgnoreCase(language) ? "en" : "zh-CN");
        workspaceMemories.capture(normalizedQuestion, settings.isUpdateMemories());
        List<String> memories = settings.isReferenceMemories()
                ? workspaceMemories.list().stream().map(item -> item.getContent()).toList()
                : List.of();
        String retrievalQuery = queryRewriter.rewrite(normalizedQuestion,
                conversationContext(knowledgeBaseId, conversationId));
        HybridRetriever.RetrievalResult retrieval = retriever.retrieveWithDiagnostics(
                knowledgeBaseId, retrievalQuery, resultLimit);
        List<RetrievedChunk> context = retrieval.chunks();
        ChatModelClient.Completion completion = chatModel.answer(normalizedQuestion, context, responseLanguage,
                new ChatModelClient.Preferences(settings.getPersonalInstructions(), memories), thinkMode);
        String answer = completion.answer();
        List<Source> sources = context.stream()
                .map(item -> new Source(item.documentId(), item.documentName(), item.chunkIndex(),
                        snippet(item.content()), Math.round(item.score() * 1000) / 1000.0))
                .toList();
        String mode = chatModel.configured() ? "model" : "local-demo";
        String reasoning = thinkMode ? reasoningSummary(completion.reasoning(), retrieval) : null;
        long thinkingDurationMs = thinkMode ? Duration.ofNanos(System.nanoTime() - started).toMillis() : 0;
        UUID savedConversationId = conversations.recordExchange(knowledgeBaseId, conversationId,
                normalizedQuestion, answer, mode, reasoning, thinkingDurationMs, thinkMode ? 1 : 0,
                sources.stream().map(source ->
                        new ConversationService.SourceSnapshot(source.documentId(), source.documentName(),
                                source.chunkIndex(), source.snippet(), source.score())).toList());
        return new ChatResult(savedConversationId, answer, mode, chatModel.modelName(), sources,
                retrieval.diagnostics(), reasoning, thinkingDurationMs, thinkMode ? 1 : 0, thinkMode);
    }

    private String reasoningSummary(String modelReasoning, HybridRetriever.RetrievalResult retrieval) {
        if (modelReasoning != null && !modelReasoning.isBlank()) return modelReasoning;
        HybridRetriever.RetrievalDiagnostics diagnostics = retrieval.diagnostics();
        return "已分析问题并执行混合检索：向量召回 %d 个候选、关键词召回 %d 个候选，融合后选取 %d 个相关片段组织回答%s。"
                .formatted(diagnostics.vectorCandidates(), diagnostics.keywordCandidates(), retrieval.chunks().size(),
                        diagnostics.reranked() ? "，并完成重排" : "");
    }

    private String snippet(String content) {
        return content.length() <= 240 ? content : content.substring(0, 240) + "...";
    }

    private List<QueryRewriter.Turn> conversationContext(UUID knowledgeBaseId, UUID conversationId) {
        if (conversationId == null) return List.of();
        ConversationService.ConversationDetail conversation = conversations.get(conversationId);
        if (!conversation.knowledgeBaseId().equals(knowledgeBaseId)) {
            throw new IllegalArgumentException("对话不属于当前知识库");
        }
        List<ConversationService.MessageView> messages = conversation.messages();
        int from = Math.max(0, messages.size() - 6);
        return messages.subList(from, messages.size()).stream()
                .map(message -> new QueryRewriter.Turn(message.role(), message.content()))
                .toList();
    }

    public record ChatResult(UUID conversationId, String answer, String mode, String model, List<Source> sources,
                             HybridRetriever.RetrievalDiagnostics retrieval, String reasoning,
                             long thinkingDurationMs, int thinkingSteps, boolean thinkMode) { }
    public record Source(UUID documentId, String documentName, int chunkIndex, String snippet, double score) { }
}
