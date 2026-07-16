package ai.jadebase.conversation.infra;

import ai.jadebase.conversation.domain.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {
    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
    long countByConversationId(UUID conversationId);
    void deleteByConversationId(UUID conversationId);
}
