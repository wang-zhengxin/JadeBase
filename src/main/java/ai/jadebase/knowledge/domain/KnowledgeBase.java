package ai.jadebase.knowledge.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_bases")
public class KnowledgeBase {

    @Id
    @GeneratedValue
    private UUID id;
    private String name;
    private String description;
    private Instant createdAt;

    protected KnowledgeBase() {
    }

    public KnowledgeBase(String name, String description) {
        this.name = name;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
