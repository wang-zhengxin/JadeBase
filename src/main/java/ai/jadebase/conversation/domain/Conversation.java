package ai.jadebase.conversation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations", indexes = @Index(name = "idx_conversation_updated", columnList = "updated_at"))
public class Conversation {

    @Id
    @GeneratedValue
    private UUID id;
    private UUID knowledgeBaseId;
    private String title;
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Conversation() {
    }

    public Conversation(UUID knowledgeBaseId, String title) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.title = title;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
