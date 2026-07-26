package ai.jadebase.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_providers")
public class ModelProvider {

    public enum Type {
        OPENAI, DEEPSEEK, DASHSCOPE, ZHIPU, MOONSHOT, VOLCENGINE, QIANFAN, SILICONFLOW,
        OPENAI_COMPATIBLE, OLLAMA, VLLM, LOCALAI, LM_STUDIO;

        public boolean local() {
            return this == OLLAMA || this == VLLM || this == LOCALAI || this == LM_STUDIO;
        }

        public boolean apiKeyRequired() {
            return !local() && this != OPENAI_COMPATIBLE;
        }
    }

    public enum Status { CONNECTED, ERROR }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 40)
    private Type providerType;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "encrypted_api_key")
    private String encryptedApiKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ModelProvider() { }

    public ModelProvider(Type providerType, String displayName, String baseUrl, String encryptedApiKey) {
        this.id = UUID.randomUUID();
        this.providerType = providerType;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.encryptedApiKey = encryptedApiKey;
        this.status = Status.CONNECTED;
        this.statusMessage = "连接正常";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(Type type, String name, String url, String encryptedKey) {
        this.providerType = type;
        this.displayName = name;
        this.baseUrl = url;
        if (encryptedKey != null) this.encryptedApiKey = encryptedKey;
        this.updatedAt = Instant.now();
    }

    public void connected(String message) {
        this.status = Status.CONNECTED;
        this.statusMessage = message;
        this.lastTestedAt = Instant.now();
        this.updatedAt = lastTestedAt;
    }

    public void failed(String message) {
        this.status = Status.ERROR;
        this.statusMessage = message == null ? "连接失败" : message.substring(0, Math.min(500, message.length()));
        this.lastTestedAt = Instant.now();
        this.updatedAt = lastTestedAt;
    }

    public UUID getId() { return id; }
    public Type getProviderType() { return providerType; }
    public String getDisplayName() { return displayName; }
    public String getBaseUrl() { return baseUrl; }
    public String getEncryptedApiKey() { return encryptedApiKey; }
    public Status getStatus() { return status; }
    public String getStatusMessage() { return statusMessage; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
