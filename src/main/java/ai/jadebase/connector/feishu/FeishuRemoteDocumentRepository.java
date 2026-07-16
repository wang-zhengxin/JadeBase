package ai.jadebase.connector.feishu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeishuRemoteDocumentRepository extends JpaRepository<FeishuRemoteDocument, UUID> {
    Optional<FeishuRemoteDocument> findBySourceIdAndRemoteToken(UUID sourceId, String remoteToken);
    @Query("select item from FeishuRemoteDocument item where item.sourceId = :sourceId "
            + "and (item.lastSeenTaskId is null or item.lastSeenTaskId <> :taskId)")
    List<FeishuRemoteDocument> findStale(@Param("sourceId") UUID sourceId, @Param("taskId") UUID taskId);

    List<FeishuRemoteDocument> findBySourceId(UUID sourceId);
}
