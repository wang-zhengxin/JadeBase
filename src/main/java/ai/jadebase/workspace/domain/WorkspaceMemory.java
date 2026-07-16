package ai.jadebase.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_memories")
public class WorkspaceMemory {

    @Id
    @GeneratedValue
    private UUID id;
    @Column(nullable = false, length = 500)
    private String content;
    private Instant createdAt;

    protected WorkspaceMemory() {
    }

    public WorkspaceMemory(String content) {
        this.content = content.trim();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
