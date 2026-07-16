package ai.jadebase.connector.feishu;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeishuSourceRepository extends JpaRepository<FeishuSource, UUID> {
    List<FeishuSource> findAllByOrderByCreatedAtDesc();
    Optional<FeishuSource> findByConnectionIdAndSourceTypeAndRemoteIdAndKnowledgeBaseId(
            UUID connectionId, FeishuSource.Type sourceType, String remoteId, UUID knowledgeBaseId);

    List<FeishuSource> findByEnabledTrue();
}
