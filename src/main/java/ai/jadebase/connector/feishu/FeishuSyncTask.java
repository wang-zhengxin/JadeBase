package ai.jadebase.connector.feishu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feishu_sync_tasks")
public class FeishuSyncTask {

    public enum Mode { FULL, INCREMENTAL }
    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue
    private UUID id;
    private UUID sourceId;
    @Enumerated(EnumType.STRING)
    private Mode mode;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(columnDefinition = "TEXT")
    private String cursorJson;
    private int scannedCount;
    private int createdCount;
    private int updatedCount;
    private int deletedCount;
    private int skippedCount;
    private int attemptCount;
    private Instant availableAt;
    private Instant leaseUntil;
    private String workerId;
    @Column(length = 1000)
    private String errorMessage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;

    protected FeishuSyncTask() { }

    public FeishuSyncTask(UUID sourceId, Mode mode) {
        this.sourceId = sourceId;
        this.mode = mode;
        this.status = Status.QUEUED;
        this.availableAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void claim(String workerId, Instant leaseUntil) {
        this.status = Status.RUNNING;
        this.workerId = workerId;
        this.leaseUntil = leaseUntil;
        this.attemptCount++;
        if (this.startedAt == null) this.startedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void heartbeat(String cursorJson, int scanned, int created, int updated, int skipped,
                          Instant leaseUntil) {
        this.cursorJson = cursorJson;
        this.scannedCount = scanned;
        this.createdCount = created;
        this.updatedCount = updated;
        this.skippedCount = skipped;
        this.leaseUntil = leaseUntil;
        this.updatedAt = Instant.now();
    }

    public void succeed(int deleted) {
        this.deletedCount = deleted;
        this.status = Status.SUCCEEDED;
        this.cursorJson = null;
        this.leaseUntil = null;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void retry(String message, Instant availableAt) {
        this.status = Status.QUEUED;
        this.errorMessage = message;
        this.availableAt = availableAt;
        this.leaseUntil = null;
        this.workerId = null;
        this.updatedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = Status.FAILED;
        this.errorMessage = message;
        this.leaseUntil = null;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void manualRetry() {
        this.status = Status.QUEUED;
        this.errorMessage = null;
        this.availableAt = Instant.now();
        this.leaseUntil = null;
        this.completedAt = null;
        this.updatedAt = Instant.now();
    }

    public void recover() {
        retry("Worker 租约超时，已从上次游标恢复", Instant.now());
    }

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public Mode getMode() { return mode; }
    public Status getStatus() { return status; }
    public String getCursorJson() { return cursorJson; }
    public int getScannedCount() { return scannedCount; }
    public int getCreatedCount() { return createdCount; }
    public int getUpdatedCount() { return updatedCount; }
    public int getDeletedCount() { return deletedCount; }
    public int getSkippedCount() { return skippedCount; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
