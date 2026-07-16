package ai.jadebase.connector.feishu;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jadebase.connectors.feishu")
public record FeishuConnectorProperties(
        String encryptionKey,
        String apiBaseUrl,
        String webBaseUrl,
        int pageSize,
        int syncIntervalMinutes,
        int maxAttempts,
        long pollDelayMs,
        long leaseSeconds,
        long recoveryDelayMs,
        boolean allowCustomBaseUrl
) {
}
