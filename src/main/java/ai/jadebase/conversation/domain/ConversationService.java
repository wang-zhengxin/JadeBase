package ai.jadebase.conversation.domain;

import ai.jadebase.conversation.infra.ConversationMessageRepository;
import ai.jadebase.conversation.infra.ConversationRepository;
import ai.jadebase.conversation.infra.MessageSourceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final MessageSourceRepository sources;

    public ConversationService(ConversationRepository conversations,
                               ConversationMessageRepository messages,
                               MessageSourceRepository sources) {
        this.conversations = conversations;
        this.messages = messages;
        this.sources = sources;
    }

    @Transactional
    public UUID recordExchange(UUID knowledgeBaseId, UUID conversationId, String question,
                               String answer, String mode, List<SourceSnapshot> sourceSnapshots) {
        Conversation conversation = conversationId == null
                ? conversations.save(new Conversation(knowledgeBaseId, title(question)))
                : requireConversation(conversationId, knowledgeBaseId);
        messages.save(new ConversationMessage(conversation.getId(), ConversationMessage.Role.USER, question, null));
        ConversationMessage assistant = messages.save(new ConversationMessage(conversation.getId(),
                ConversationMessage.Role.ASSISTANT, answer, mode));
        for (int i = 0; i < sourceSnapshots.size(); i++) {
            SourceSnapshot source = sourceSnapshots.get(i);
            sources.save(new MessageSource(assistant.getId(), i, source.documentId(), source.documentName(),
                    source.chunkIndex(), source.snippet(), source.score()));
        }
        conversation.touch();
        conversations.save(conversation);
        return conversation.getId();
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(String query) {
        List<Conversation> result = query == null || query.isBlank()
                ? conversations.findAllByOrderByUpdatedAtDesc()
                : conversations.findByTitleContainingIgnoreCaseOrderByUpdatedAtDesc(query.trim());
        return result.stream().map(item -> new ConversationSummary(item.getId(), item.getKnowledgeBaseId(),
                item.getTitle(), messages.countByConversationId(item.getId()), item.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(UUID conversationId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("会话不存在"));
        List<ConversationMessage> messageList = messages.findByConversationIdOrderByCreatedAtAsc(conversationId);
        Collection<UUID> messageIds = messageList.stream().map(ConversationMessage::getId).toList();
        Map<UUID, List<MessageSource>> sourcesByMessage = messageIds.isEmpty() ? Map.of()
                : sources.findByMessageIdInOrderBySourceIndexAsc(messageIds).stream()
                .collect(Collectors.groupingBy(MessageSource::getMessageId));
        List<MessageView> views = messageList.stream().map(message -> new MessageView(message.getId(),
                message.getRole().name().toLowerCase(), message.getContent(), message.getMode(),
                message.getCreatedAt(), sourcesByMessage.getOrDefault(message.getId(), List.of()).stream()
                .map(SourceView::from).toList())).toList();
        return new ConversationDetail(conversation.getId(), conversation.getKnowledgeBaseId(),
                conversation.getTitle(), conversation.getCreatedAt(), conversation.getUpdatedAt(), views);
    }

    @Transactional
    public void delete(UUID conversationId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("会话不存在"));
        List<UUID> messageIds = messages.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(ConversationMessage::getId).toList();
        if (!messageIds.isEmpty()) sources.deleteByMessageIdIn(messageIds);
        messages.deleteByConversationId(conversationId);
        conversations.delete(conversation);
    }

    private Conversation requireConversation(UUID conversationId, UUID knowledgeBaseId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("会话不存在"));
        if (!conversation.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new IllegalArgumentException("会话与当前知识库不匹配");
        }
        return conversation;
    }

    private String title(String question) {
        String normalized = question.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48) + "...";
    }

    public record SourceSnapshot(UUID documentId, String documentName, int chunkIndex,
                                 String snippet, double score) { }
    public record ConversationSummary(UUID id, UUID knowledgeBaseId, String title,
                                      long messageCount, Instant updatedAt) { }
    public record ConversationDetail(UUID id, UUID knowledgeBaseId, String title, Instant createdAt,
                                     Instant updatedAt, List<MessageView> messages) { }
    public record MessageView(UUID id, String role, String content, String mode, Instant createdAt,
                              List<SourceView> sources) { }
    public record SourceView(UUID documentId, String documentName, int chunkIndex,
                             String snippet, double score) {
        static SourceView from(MessageSource source) {
            return new SourceView(source.getDocumentId(), source.getDocumentName(), source.getChunkIndex(),
                    source.getSnippet(), source.getScore());
        }
    }
}
