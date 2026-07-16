package ai.jadebase.connector.feishu;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeishuSyncTaskRepository extends JpaRepository<FeishuSyncTask, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from FeishuSyncTask task where task.status = :status and task.availableAt <= :now order by task.createdAt")
    List<FeishuSyncTask> findClaimable(@Param("status") FeishuSyncTask.Status status,
                                       @Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from FeishuSyncTask task where task.status = :status and task.leaseUntil < :now")
    List<FeishuSyncTask> findExpired(@Param("status") FeishuSyncTask.Status status,
                                     @Param("now") Instant now);

    boolean existsBySourceIdAndStatusIn(UUID sourceId, List<FeishuSyncTask.Status> statuses);
    Optional<FeishuSyncTask> findTopBySourceIdOrderByCreatedAtDesc(UUID sourceId);
    List<FeishuSyncTask> findTop20ByOrderByCreatedAtDesc();
}
