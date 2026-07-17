package ai.jadebase.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRuntimeResolverTest {

    @Test
    void preservesLegacyHostOnlyEnvironmentBaseUrl() {
        assertThat(ModelRuntimeResolver.environmentApiRoot("https://api.deepseek.com"))
                .isEqualTo("https://api.deepseek.com/v1");
        assertThat(ModelRuntimeResolver.environmentApiRoot("https://example.com/openai/v1/"))
                .isEqualTo("https://example.com/openai/v1");
    }
}
