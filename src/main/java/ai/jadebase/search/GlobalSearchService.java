package ai.jadebase.search;

import ai.jadebase.agent.AgentService;
import ai.jadebase.conversation.domain.ConversationService;
import ai.jadebase.identity.domain.JadeUser;
import ai.jadebase.knowledge.domain.Document;
import ai.jadebase.knowledge.domain.KnowledgeBase;
import ai.jadebase.knowledge.infra.DocumentRepository;
import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class GlobalSearchService {

    private static final int RESULT_LIMIT = 6;

    private final ConversationService conversations;
    private final KnowledgeBaseRepository knowledgeBases;
    private final DocumentRepository documents;
    private final AgentService agents;

    public GlobalSearchService(ConversationService conversations, KnowledgeBaseRepository knowledgeBases,
                               DocumentRepository documents, AgentService agents) {
        this.conversations = conversations;
        this.knowledgeBases = knowledgeBases;
        this.documents = documents;
        this.agents = agents;
    }

    @Transactional(readOnly = true)
    public SearchResult search(String query, JadeUser actor) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() > 120) throw new IllegalArgumentException("搜索内容不能超过 120 个字符");

        List<KnowledgeBase> knowledgeMatches = knowledgeBases
                .findTop6ByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrderByCreatedAtDesc(
                        normalized, normalized);
        List<Document> documentMatches = documents
                .findTop6ByNameContainingIgnoreCaseOrderByCreatedAtDesc(normalized);
        Map<UUID, String> knowledgeNames = knowledgeBases.findAllById(documentMatches.stream()
                        .map(Document::getKnowledgeBaseId).distinct().toList())
                .stream().collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));

        String lowered = normalized.toLowerCase(Locale.ROOT);
        Predicate<AgentService.AvailableAgentView> agentMatch = item -> lowered.isBlank()
                || contains(item.name(), lowered) || contains(item.description(), lowered)
                || contains(item.knowledgeBaseName(), lowered)
                || item.labels().stream().anyMatch(label -> contains(label, lowered));

        List<ConversationResult> conversationResults = conversations.list(normalized).stream()
                .limit(RESULT_LIMIT)
                .map(item -> new ConversationResult(item.id(), item.knowledgeBaseId(), item.title(),
                        item.messageCount(), item.updatedAt()))
                .toList();
        List<KnowledgeBaseResult> knowledgeResults = knowledgeMatches.stream()
                .map(item -> new KnowledgeBaseResult(item.getId(), item.getName(), item.getDescription(),
                        item.getCreatedAt()))
                .toList();
        List<DocumentResult> documentResults = documentMatches.stream()
                .map(item -> new DocumentResult(item.getId(), item.getKnowledgeBaseId(), item.getName(),
                        knowledgeNames.getOrDefault(item.getKnowledgeBaseId(), "未知知识库"),
                        item.getStatus().name().toLowerCase(Locale.ROOT), item.getCreatedAt()))
                .toList();
        List<AgentResult> agentResults = agents.listAvailable(actor).stream()
                .filter(agentMatch).limit(RESULT_LIMIT)
                .map(item -> new AgentResult(item.id(), item.name(), item.description(),
                        item.knowledgeBaseId(), item.knowledgeBaseName(), item.thinkMode(), item.featured()))
                .toList();
        return new SearchResult(normalized, conversationResults, knowledgeResults, documentResults, agentResults);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    public record SearchResult(String query, List<ConversationResult> conversations,
                               List<KnowledgeBaseResult> knowledgeBases, List<DocumentResult> documents,
                               List<AgentResult> agents) { }
    public record ConversationResult(UUID id, UUID knowledgeBaseId, String title,
                                     long messageCount, Instant updatedAt) { }
    public record KnowledgeBaseResult(UUID id, String name, String description, Instant createdAt) { }
    public record DocumentResult(UUID id, UUID knowledgeBaseId, String name, String knowledgeBaseName,
                                 String status, Instant createdAt) { }
    public record AgentResult(UUID id, String name, String description, UUID knowledgeBaseId,
                              String knowledgeBaseName, boolean thinkMode, boolean featured) { }
}
