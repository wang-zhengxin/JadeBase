package ai.jadebase.knowledge.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_index_tasks", indexes = {
        @Index(name = "idx_index_task_poll", columnList = "status,available_at"),
        @Index(name = "idx_index_task_document", columnList = "document_id,created_at")
})
public class DocumentIndexTask {

    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue
    private UUID id;
    private UUID documentId;
    @Enumerated(EnumType.STRING)
    private Status status;
    private int attempt;
    private Instant availableAt;
    private Instant leaseUntil;
    private String workerId;
    @Column(length = 1000)
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;

    protected DocumentIndexTask() {
    }

    public DocumentIndexTask(UUID documentId) {
        this.documentId = documentId;
        this.status = Status.QUEUED;
        this.availableAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void claim(String workerId, Instant leaseUntil) {
        if (status != Status.QUEUED) throw new IllegalStateException("任务不在排队状态");
        this.status = Status.RUNNING;
        this.workerId = workerId;
        this.leaseUntil = leaseUntil;
        this.attempt++;
        this.updatedAt = Instant.now();
    }

    public void heartbeat(Instant leaseUntil) {
        if (status == Status.RUNNING) {
            this.leaseUntil = leaseUntil;
            this.updatedAt = Instant.now();
        }
    }

    public void succeed() {
        this.status = Status.SUCCEEDED;
        this.leaseUntil = null;
        this.updatedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = Status.FAILED;
        this.lastError = message;
        this.leaseUntil = null;
        this.updatedAt = Instant.now();
    }

    public void recover() {
        this.status = Status.QUEUED;
        this.availableAt = Instant.now();
        this.leaseUntil = null;
        this.workerId = null;
        this.lastError = "Worker 租约超时，任务已自动恢复";
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public Status getStatus() { return status; }
    public Instant getLeaseUntil() { return leaseUntil; }
}
