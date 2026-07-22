package ai.jadebase.rag.application;

import ai.jadebase.agent.AgentService;
import ai.jadebase.conversation.domain.ConversationService;
import ai.jadebase.identity.domain.JadeUser;
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
    private final AgentService agents;

    public ChatService(KnowledgeBaseRepository knowledgeBases, HybridRetriever retriever,
                       ChatModelClient chatModel, ConversationService conversations,
                       QueryRewriter queryRewriter, WorkspaceSettingsService workspaceSettings,
                       WorkspaceMemoryService workspaceMemories, AgentService agents) {
        this.knowledgeBases = knowledgeBases;
        this.retriever = retriever;
        this.chatModel = chatModel;
        this.conversations = conversations;
        this.queryRewriter = queryRewriter;
        this.workspaceSettings = workspaceSettings;
        this.workspaceMemories = workspaceMemories;
        this.agents = agents;
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
        return ask(knowledgeBaseId, conversationId, question, topK, language, thinkMode, null, null);
    }

    public ChatResult ask(UUID knowledgeBaseId, UUID conversationId, String question,
                          Integer topK, String language, boolean thinkMode, UUID agentId, JadeUser actor) {
        long started = System.nanoTime();
        if (question == null || question.isBlank()) throw new IllegalArgumentException("问题不能为空");
        String normalizedQuestion = question.trim();
        AgentService.RuntimeConfig agent = agentId == null ? null : agents.runtime(agentId, actor);
        boolean useKnowledge = agent == null || agent.useKnowledge();
        UUID effectiveKnowledgeBaseId = agent != null && agent.useKnowledge()
                ? agent.knowledgeBaseId() : knowledgeBaseId;
        boolean effectiveThinkMode = thinkMode || agent != null && agent.thinkMode();
        if (!knowledgeBases.existsById(effectiveKnowledgeBaseId)) throw new EntityNotFoundException("知识库不存在");
        UUID runId = agent == null ? null : agents.startRun(agent, actor, normalizedQuestion);
        try {
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
        String retrievalQuery = useKnowledge ? queryRewriter.rewrite(normalizedQuestion,
                conversationContext(effectiveKnowledgeBaseId, conversationId)) : normalizedQuestion;
        HybridRetriever.RetrievalResult retrieval = useKnowledge
                ? retriever.retrieveWithDiagnostics(effectiveKnowledgeBaseId, retrievalQuery, resultLimit)
                : new HybridRetriever.RetrievalResult(List.of(),
                        new HybridRetriever.RetrievalDiagnostics(retrievalQuery, 0, 0, 0, false, 0));
        List<RetrievedChunk> context = retrieval.chunks();
        ChatModelClient.Completion completion = chatModel.answer(normalizedQuestion, context, responseLanguage,
                new ChatModelClient.Preferences(settings.getPersonalInstructions(), memories,
                        agent == null ? "" : agent.systemPrompt(), agent == null ? null : agent.modelProviderId(),
                        agent == null ? null : agent.modelId()), effectiveThinkMode);
        String answer = completion.answer();
        List<Source> sources = context.stream()
                .map(item -> new Source(item.documentId(), item.documentName(), item.chunkIndex(),
                        snippet(item.content()), Math.round(item.score() * 1000) / 1000.0))
                .toList();
        String mode = completion.configured() ? "model" : "local-demo";
        String reasoning = effectiveThinkMode
                ? reasoningSummary(completion.reasoning(), retrieval, useKnowledge) : null;
        long thinkingDurationMs = effectiveThinkMode ? Duration.ofNanos(System.nanoTime() - started).toMillis() : 0;
        UUID savedConversationId = conversations.recordExchange(effectiveKnowledgeBaseId, conversationId,
                normalizedQuestion, answer, mode, reasoning, thinkingDurationMs, effectiveThinkMode ? 1 : 0,
                sources.stream().map(source ->
                        new ConversationService.SourceSnapshot(source.documentId(), source.documentName(),
                                source.chunkIndex(), source.snippet(), source.score())).toList());
        if (runId != null) agents.completeRun(runId, savedConversationId, answer);
        return new ChatResult(savedConversationId, answer, mode, completion.modelName(), sources,
                retrieval.diagnostics(), reasoning, thinkingDurationMs, effectiveThinkMode ? 1 : 0,
                effectiveThinkMode, agentId, agent == null ? null : agent.name(),
                agent == null ? null : agent.version());
        } catch (RuntimeException exception) {
            if (runId != null) agents.failRun(runId, exception);
            throw exception;
        }
    }

    private String reasoningSummary(String modelReasoning, HybridRetriever.RetrievalResult retrieval,
                                    boolean useKnowledge) {
        if (modelReasoning != null && !modelReasoning.isBlank()) return modelReasoning;
        if (!useKnowledge) return "已根据 Agent 指令直接分析问题，本次未启用知识库检索。";
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
                             long thinkingDurationMs, int thinkingSteps, boolean thinkMode,
                             UUID agentId, String agentName, Integer agentVersion) { }
    public record Source(UUID documentId, String documentName, int chunkIndex, String snippet, double score) { }
}
