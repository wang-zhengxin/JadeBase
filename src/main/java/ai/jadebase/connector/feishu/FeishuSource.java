package ai.jadebase.connector.feishu;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feishu_sources")
public class FeishuSource {

    public enum Type { WIKI, FOLDER }

    @Id
    @GeneratedValue
    private UUID id;
    private UUID connectionId;
    private UUID knowledgeBaseId;
    @Enumerated(EnumType.STRING)
    private Type sourceType;
    private String remoteId;
    private String remoteName;
    private boolean enabled;
    private int syncIntervalMinutes;
    private Instant lastSyncStartedAt;
    private Instant lastSyncCompletedAt;
    private String lastSyncStatus;
    @Column(length = 1000)
    private String lastSyncMessage;
    private Instant createdAt;
    private Instant updatedAt;

    protected FeishuSource() { }

    public FeishuSource(UUID connectionId, UUID knowledgeBaseId, Type sourceType,
                        String remoteId, String remoteName, int syncIntervalMinutes) {
        this.connectionId = connectionId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.sourceType = sourceType;
        this.remoteId = remoteId;
        this.remoteName = remoteName;
        this.enabled = true;
        this.syncIntervalMinutes = syncIntervalMinutes;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markStarted() {
        this.lastSyncStartedAt = Instant.now();
        this.lastSyncStatus = "RUNNING";
        this.lastSyncMessage = "正在同步";
        this.updatedAt = Instant.now();
    }

    public void markCompleted(String message) {
        this.lastSyncCompletedAt = Instant.now();
        this.lastSyncStatus = "SUCCEEDED";
        this.lastSyncMessage = message;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String message) {
        this.lastSyncStatus = "FAILED";
        this.lastSyncMessage = message;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getConnectionId() { return connectionId; }
    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public Type getSourceType() { return sourceType; }
    public String getRemoteId() { return remoteId; }
    public String getRemoteName() { return remoteName; }
    public boolean isEnabled() { return enabled; }
    public int getSyncIntervalMinutes() { return syncIntervalMinutes; }
    public Instant getLastSyncStartedAt() { return lastSyncStartedAt; }
    public Instant getLastSyncCompletedAt() { return lastSyncCompletedAt; }
    public String getLastSyncStatus() { return lastSyncStatus; }
    public String getLastSyncMessage() { return lastSyncMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
