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

    public enum Status { PROCESSING, READY, FAILED }

    @Id
    @GeneratedValue
    private UUID id;
    private UUID knowledgeBaseId;
    private String name;
    private String contentType;
    private long sizeBytes;
    private int chunkCount;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String errorMessage;
    private Instant createdAt;

    protected Document() {
    }

    public Document(UUID knowledgeBaseId, String name, String contentType, long sizeBytes) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.name = name;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = Status.PROCESSING;
        this.createdAt = Instant.now();
    }

    public void markReady(int chunkCount) {
        this.chunkCount = chunkCount;
        this.status = Status.READY;
    }

    public void markFailed(String message) {
        this.status = Status.FAILED;
        this.errorMessage = message;
    }

    public UUID getId() { return id; }
    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getName() { return name; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getChunkCount() { return chunkCount; }
    public Status getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
