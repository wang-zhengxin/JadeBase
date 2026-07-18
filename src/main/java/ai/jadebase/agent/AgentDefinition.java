package ai.jadebase.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agents")
public class AgentDefinition {

    public enum AccessLevel { EVERYONE, PRIVATE }
    public enum Status { DRAFT, PUBLISHED }

    @Id
    private UUID id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(name = "system_prompt", nullable = false)
    private String systemPrompt;
    @Column(name = "knowledge_base_id", nullable = false)
    private UUID knowledgeBaseId;
    @Column(name = "model_provider_id")
    private UUID modelProviderId;
    @Column(name = "model_id", length = 255)
    private String modelId;
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 24)
    private AccessLevel accessLevel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "think_mode", nullable = false)
    private boolean thinkMode;
    @Column(name = "max_iterations", nullable = false)
    private int maxIterations;
    @Column(name = "current_version", nullable = false)
    private int currentVersion;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentDefinition() { }

    public AgentDefinition(String name, String description, String systemPrompt, UUID knowledgeBaseId,
                           UUID modelProviderId, String modelId, AccessLevel accessLevel,
                           boolean thinkMode, int maxIterations, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.createdBy = createdBy;
        this.status = Status.DRAFT;
        this.enabled = true;
        this.currentVersion = 0;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        update(name, description, systemPrompt, knowledgeBaseId, modelProviderId, modelId,
                accessLevel, thinkMode, maxIterations);
    }

    public void update(String name, String description, String systemPrompt, UUID knowledgeBaseId,
                       UUID modelProviderId, String modelId, AccessLevel accessLevel,
                       boolean thinkMode, int maxIterations) {
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.knowledgeBaseId = knowledgeBaseId;
        this.modelProviderId = modelProviderId;
        this.modelId = modelId;
        this.accessLevel = accessLevel;
        this.thinkMode = thinkMode;
        this.maxIterations = maxIterations;
        if (currentVersion > 0) this.status = Status.DRAFT;
        this.updatedAt = Instant.now();
    }

    public void publish(int version) {
        this.currentVersion = version;
        this.status = Status.PUBLISHED;
        this.enabled = true;
        this.publishedAt = Instant.now();
        this.updatedAt = publishedAt;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSystemPrompt() { return systemPrompt; }
    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public UUID getModelProviderId() { return modelProviderId; }
    public String getModelId() { return modelId; }
    public AccessLevel getAccessLevel() { return accessLevel; }
    public Status getStatus() { return status; }
    public boolean isEnabled() { return enabled; }
    public boolean isThinkMode() { return thinkMode; }
    public int getMaxIterations() { return maxIterations; }
    public int getCurrentVersion() { return currentVersion; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
