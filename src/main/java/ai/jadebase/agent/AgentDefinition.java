package ai.jadebase.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
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
    @Column(name = "conversation_starters_json", nullable = false)
    private String conversationStartersJson;
    @Column(name = "use_knowledge", nullable = false)
    private boolean useKnowledge;
    @Column(name = "knowledge_base_id")
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
    @Column(nullable = false)
    private boolean featured;
    @Column(name = "labels_json", nullable = false)
    private String labelsJson;
    @Column(name = "enabled_actions_json", nullable = false)
    private String enabledActionsJson;
    @Column(name = "knowledge_cutoff_date")
    private LocalDate knowledgeCutoffDate;
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

    public AgentDefinition(Configuration configuration, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.createdBy = createdBy;
        this.status = Status.DRAFT;
        this.enabled = true;
        this.currentVersion = 0;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        update(configuration);
    }

    public void update(Configuration configuration) {
        this.name = configuration.name();
        this.description = configuration.description();
        this.systemPrompt = configuration.systemPrompt();
        this.conversationStartersJson = configuration.conversationStartersJson();
        this.useKnowledge = configuration.useKnowledge();
        this.knowledgeBaseId = configuration.knowledgeBaseId();
        this.modelProviderId = configuration.modelProviderId();
        this.modelId = configuration.modelId();
        this.accessLevel = configuration.accessLevel();
        this.thinkMode = configuration.thinkMode();
        this.maxIterations = configuration.maxIterations();
        this.featured = configuration.featured();
        this.labelsJson = configuration.labelsJson();
        this.enabledActionsJson = configuration.enabledActionsJson();
        this.knowledgeCutoffDate = configuration.knowledgeCutoffDate();
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
    public String getConversationStartersJson() { return conversationStartersJson; }
    public boolean isUseKnowledge() { return useKnowledge; }
    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public UUID getModelProviderId() { return modelProviderId; }
    public String getModelId() { return modelId; }
    public AccessLevel getAccessLevel() { return accessLevel; }
    public Status getStatus() { return status; }
    public boolean isEnabled() { return enabled; }
    public boolean isThinkMode() { return thinkMode; }
    public int getMaxIterations() { return maxIterations; }
    public boolean isFeatured() { return featured; }
    public String getLabelsJson() { return labelsJson; }
    public String getEnabledActionsJson() { return enabledActionsJson; }
    public LocalDate getKnowledgeCutoffDate() { return knowledgeCutoffDate; }
    public int getCurrentVersion() { return currentVersion; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public record Configuration(String name, String description, String systemPrompt,
                                String conversationStartersJson, boolean useKnowledge,
                                UUID knowledgeBaseId, UUID modelProviderId, String modelId,
                                AccessLevel accessLevel, boolean thinkMode, int maxIterations,
                                boolean featured, String labelsJson, String enabledActionsJson,
                                LocalDate knowledgeCutoffDate) { }
}
