package ai.jadebase.conversation.infra;

import ai.jadebase.conversation.domain.MessageSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageSourceRepository extends JpaRepository<MessageSource, UUID> {
    List<MessageSource> findByMessageIdInOrderBySourceIndexAsc(Collection<UUID> messageIds);
    void deleteByMessageIdIn(Collection<UUID> messageIds);
}
