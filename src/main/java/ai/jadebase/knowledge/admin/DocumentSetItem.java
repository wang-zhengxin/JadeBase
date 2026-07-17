package ai.jadebase.knowledge.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_set_items")
public class DocumentSetItem {

    @Id
    private UUID id;

    @Column(name = "document_set_id", nullable = false)
    private UUID documentSetId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentSetItem() { }

    public DocumentSetItem(UUID documentSetId, UUID documentId) {
        this.id = UUID.randomUUID();
        this.documentSetId = documentSetId;
        this.documentId = documentId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDocumentSetId() { return documentSetId; }
    public UUID getDocumentId() { return documentId; }
    public Instant getCreatedAt() { return createdAt; }
}
