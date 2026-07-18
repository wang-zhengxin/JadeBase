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
@Table(name = "agent_versions")
public class AgentVersion {

    @Id
    private UUID id;
    @Column(name = "agent_id", nullable = false)
    private UUID agentId;
    @Column(nullable = false)
    private int version;
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
    private AgentDefinition.AccessLevel accessLevel;
    @Column(name = "think_mode", nullable = false)
    private boolean thinkMode;
    @Column(name = "max_iterations", nullable = false)
    private int maxIterations;
    @Column(name = "published_by", nullable = false)
    private UUID publishedBy;
    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected AgentVersion() { }

    public AgentVersion(AgentDefinition agent, int version, UUID publishedBy) {
        this.id = UUID.randomUUID();
        this.agentId = agent.getId();
        this.version = version;
        this.name = agent.getName();
        this.description = agent.getDescription();
        this.systemPrompt = agent.getSystemPrompt();
        this.knowledgeBaseId = agent.getKnowledgeBaseId();
        this.modelProviderId = agent.getModelProviderId();
        this.modelId = agent.getModelId();
        this.accessLevel = agent.getAccessLevel();
        this.thinkMode = agent.isThinkMode();
        this.maxIterations = agent.getMaxIterations();
        this.publishedBy = publishedBy;
        this.publishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAgentId() { return agentId; }
    public int getVersion() { return version; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSystemPrompt() { return systemPrompt; }
    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public UUID getModelProviderId() { return modelProviderId; }
    public String getModelId() { return modelId; }
    public AgentDefinition.AccessLevel getAccessLevel() { return accessLevel; }
    public boolean isThinkMode() { return thinkMode; }
    public int getMaxIterations() { return maxIterations; }
    public UUID getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
}
