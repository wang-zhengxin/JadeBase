package ai.jadebase.knowledge.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

    public enum Status { QUEUED, PROCESSING, READY, FAILED }

    @Id
    @GeneratedValue
    private UUID id;
    private UUID knowledgeBaseId;
    private String name;
    private String contentType;
    private long sizeBytes;
    private int chunkCount;
    private int progress;
    private int attemptCount;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String errorMessage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    protected Document() {
    }

    public Document(UUID knowledgeBaseId, String name, String contentType, long sizeBytes) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.name = name;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = Status.QUEUED;
        this.createdAt = Instant.now();
    }

    public void markProcessing() {
        this.status = Status.PROCESSING;
        this.progress = 10;
        this.attemptCount++;
        this.startedAt = Instant.now();
        this.completedAt = null;
        this.errorMessage = null;
    }

    public void updateProgress(int progress) {
        if (status == Status.PROCESSING) this.progress = Math.min(95, Math.max(10, progress));
    }

    public void markReady(int chunkCount) {
        this.chunkCount = chunkCount;
        this.progress = 100;
        this.status = Status.READY;
        this.completedAt = Instant.now();
    }

    public void markFailed(String message) {
        this.status = Status.FAILED;
        this.errorMessage = message;
        this.completedAt = Instant.now();
    }

    public void queueForRetry() {
        if (status != Status.FAILED) throw new IllegalStateException("只有失败的文档可以重试");
        this.status = Status.QUEUED;
        this.progress = 0;
        this.errorMessage = null;
        this.startedAt = null;
        this.completedAt = null;
    }

    public UUID getId() { return id; }
    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getName() { return name; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getChunkCount() { return chunkCount; }
    public int getProgress() { return progress; }
    public int getAttemptCount() { return attemptCount; }
    public Status getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
