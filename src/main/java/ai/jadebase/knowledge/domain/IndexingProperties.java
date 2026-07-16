package ai.jadebase.knowledge.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jadebase.indexing")
public record IndexingProperties(long pollDelayMs, long leaseSeconds, long recoveryDelayMs) {
}
