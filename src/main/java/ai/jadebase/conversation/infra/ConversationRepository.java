package ai.jadebase.conversation.infra;

import ai.jadebase.conversation.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findAllByOrderByUpdatedAtDesc();
    List<Conversation> findByTitleContainingIgnoreCaseOrderByUpdatedAtDesc(String query);
}
