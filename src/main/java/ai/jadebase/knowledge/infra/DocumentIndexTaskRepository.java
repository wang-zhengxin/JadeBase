package ai.jadebase.knowledge.infra;

import ai.jadebase.knowledge.domain.DocumentIndexTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DocumentIndexTaskRepository extends JpaRepository<DocumentIndexTask, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from DocumentIndexTask task where task.status = :status "
            + "and task.availableAt <= :now order by task.createdAt")
    List<DocumentIndexTask> findClaimable(@Param("status") DocumentIndexTask.Status status,
                                          @Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from DocumentIndexTask task where task.status = :status and task.leaseUntil < :now")
    List<DocumentIndexTask> findExpired(@Param("status") DocumentIndexTask.Status status,
                                        @Param("now") Instant now);

    void deleteByDocumentId(UUID documentId);
    java.util.Optional<DocumentIndexTask> findTopByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
