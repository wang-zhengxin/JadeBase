package ai.jadebase.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "language_models")
public class LanguageModel {

    @Id
    private UUID id;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "model_id", nullable = false, length = 255)
    private String modelId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "default_model", nullable = false)
    private boolean defaultModel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LanguageModel() { }

    public LanguageModel(UUID providerId, String modelId) {
        this.id = UUID.randomUUID();
        this.providerId = providerId;
        this.modelId = modelId;
        this.displayName = modelId;
        this.enabled = true;
        this.defaultModel = false;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void enable() {
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    public void setDefaultModel(boolean value) {
        this.defaultModel = value;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public String getModelId() { return modelId; }
    public String getDisplayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
    public boolean isDefaultModel() { return defaultModel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
