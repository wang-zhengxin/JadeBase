package ai.jadebase.connector.feishu;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feishu_remote_documents")
public class FeishuRemoteDocument {

    @Id
    @GeneratedValue
    private UUID id;
    private UUID sourceId;
    private UUID documentId;
    private String remoteToken;
    private String nodeToken;
    private String remoteParentToken;
    private String remoteType;
    @Column(length = 500)
    private String title;
    private String author;
    @Column(length = 1000)
    private String sourceUrl;
    private String remoteRevision;
    private Instant remoteUpdatedAt;
    private String contentHash;
    private UUID lastSeenTaskId;
    private Instant createdAt;
    private Instant updatedAt;

    protected FeishuRemoteDocument() { }

    public FeishuRemoteDocument(UUID sourceId, UUID documentId, FeishuApiClient.RemoteItem item,
                                String revision, String contentHash, UUID taskId) {
        this.sourceId = sourceId;
        this.documentId = documentId;
        this.remoteToken = item.objectToken();
        this.createdAt = Instant.now();
        update(item, revision, contentHash, taskId);
    }

    public void seen(FeishuApiClient.RemoteItem item, UUID taskId) {
        this.nodeToken = item.nodeToken();
        this.remoteParentToken = item.parentToken();
        this.title = item.title();
        this.author = item.author();
        this.sourceUrl = item.sourceUrl();
        this.remoteUpdatedAt = item.updatedAt();
        this.lastSeenTaskId = taskId;
        this.updatedAt = Instant.now();
    }

    public void update(FeishuApiClient.RemoteItem item, String revision, String contentHash, UUID taskId) {
        seen(item, taskId);
        this.remoteType = item.objectType();
        this.remoteRevision = revision;
        this.contentHash = contentHash;
    }

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public UUID getDocumentId() { return documentId; }
    public String getRemoteToken() { return remoteToken; }
    public String getNodeToken() { return nodeToken; }
    public String getRemoteParentToken() { return remoteParentToken; }
    public String getRemoteType() { return remoteType; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getSourceUrl() { return sourceUrl; }
    public String getRemoteRevision() { return remoteRevision; }
    public Instant getRemoteUpdatedAt() { return remoteUpdatedAt; }
    public String getContentHash() { return contentHash; }
    public UUID getLastSeenTaskId() { return lastSeenTaskId; }
}
