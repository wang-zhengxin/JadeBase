package ai.jadebase.connector.feishu;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feishu_connections")
public class FeishuConnection {

    public enum Status { CONNECTED, ERROR }

    @Id
    @GeneratedValue
    private UUID id;
    @Column(length = 120)
    private String name;
    @Column(length = 120)
    private String appId;
    @Column(columnDefinition = "TEXT")
    private String encryptedAppSecret;
    @Column(length = 500)
    private String apiBaseUrl;
    @Column(length = 500)
    private String webBaseUrl;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(length = 500)
    private String statusMessage;
    private Instant lastConnectedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected FeishuConnection() { }

    public FeishuConnection(String name, String appId, String encryptedAppSecret,
                            String apiBaseUrl, String webBaseUrl) {
        this.name = name;
        this.appId = appId;
        this.encryptedAppSecret = encryptedAppSecret;
        this.apiBaseUrl = apiBaseUrl;
        this.webBaseUrl = webBaseUrl;
        this.status = Status.CONNECTED;
        this.statusMessage = "连接成功";
        this.lastConnectedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, String appId, String encryptedAppSecret,
                       String apiBaseUrl, String webBaseUrl) {
        this.name = name;
        this.appId = appId;
        if (encryptedAppSecret != null) this.encryptedAppSecret = encryptedAppSecret;
        this.apiBaseUrl = apiBaseUrl;
        this.webBaseUrl = webBaseUrl;
        connected();
    }

    public void connected() {
        this.status = Status.CONNECTED;
        this.statusMessage = "连接成功";
        this.lastConnectedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void failed(String message) {
        this.status = Status.ERROR;
        this.statusMessage = message;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getAppId() { return appId; }
    @JsonIgnore
    public String getEncryptedAppSecret() { return encryptedAppSecret; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public String getWebBaseUrl() { return webBaseUrl; }
    public Status getStatus() { return status; }
    public String getStatusMessage() { return statusMessage; }
    public Instant getLastConnectedAt() { return lastConnectedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
