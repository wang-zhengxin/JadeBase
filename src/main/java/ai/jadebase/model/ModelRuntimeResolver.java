package ai.jadebase.model;

import ai.jadebase.common.CredentialCipher;
import ai.jadebase.rag.infra.ModelProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;

@Component
public class ModelRuntimeResolver {

    private final LanguageModelRepository models;
    private final ModelProviderRepository providers;
    private final CredentialCipher cipher;
    private final ModelProperties fallback;

    public ModelRuntimeResolver(LanguageModelRepository models, ModelProviderRepository providers,
                                CredentialCipher cipher, ModelProperties fallback) {
        this.models = models;
        this.providers = providers;
        this.cipher = cipher;
        this.fallback = fallback;
    }

    @Transactional(readOnly = true)
    public RuntimeModel current() {
        return current(LanguageModel.Capability.CHAT);
    }

    @Transactional(readOnly = true)
    public RuntimeModel current(LanguageModel.Capability capability) {
        return models.findFirstByCapabilityAndDefaultModelTrueAndEnabledTrueOrderByCreatedAtAsc(capability)
                .flatMap(model -> providers.findById(model.getProviderId()).map(provider -> new RuntimeModel(
                        provider.getBaseUrl(), cipher.decrypt(provider.getEncryptedApiKey()), model.getModelId(),
                        provider.getDisplayName(), capability, model.getEmbeddingDimensions(), true, "database")))
                .orElseGet(() -> environmentFallback(capability));
    }

    @Transactional(readOnly = true)
    public RuntimeModel resolve(UUID providerId, String modelId) {
        if (providerId == null || modelId == null || modelId.isBlank()) return current();
        LanguageModel model = models.findByProviderIdAndModelId(providerId, modelId)
                .filter(LanguageModel::isEnabled)
                .orElseThrow(() -> new EntityNotFoundException("Agent 配置的模型不存在或未启用"));
        if (model.getCapability() != LanguageModel.Capability.CHAT) {
            throw new EntityNotFoundException("Agent 只能使用对话模型");
        }
        ModelProvider provider = providers.findById(model.getProviderId())
                .orElseThrow(() -> new EntityNotFoundException("Agent 配置的模型供应商不存在"));
        return new RuntimeModel(provider.getBaseUrl(), cipher.decrypt(provider.getEncryptedApiKey()),
                model.getModelId(), provider.getDisplayName(), model.getCapability(),
                model.getEmbeddingDimensions(), true, "agent");
    }

    private RuntimeModel environmentFallback(LanguageModel.Capability capability) {
        String baseUrl;
        String apiKey;
        String modelId;
        Integer dimensions = null;
        boolean configured;
        if (capability == LanguageModel.Capability.EMBEDDING) {
            baseUrl = fallback.embeddingBaseUrl();
            apiKey = fallback.embeddingApiKey();
            modelId = fallback.embeddingModel();
            dimensions = fallback.embeddingDimensions();
            configured = fallback.hasEmbeddingModel();
        } else if (capability == LanguageModel.Capability.RERANKER) {
            baseUrl = fallback.rerankerBaseUrl();
            apiKey = fallback.rerankerApiKey();
            modelId = fallback.rerankerModel();
            configured = fallback.hasReranker();
        } else {
            baseUrl = fallback.baseUrl();
            apiKey = fallback.apiKey();
            modelId = fallback.chatModel();
            configured = fallback.hasChatModel();
        }
        String fallbackName = capability == LanguageModel.Capability.CHAT ? "本地演示" : "未配置";
        String modelName = modelId == null || modelId.isBlank() ? fallbackName : modelId;
        return new RuntimeModel(environmentApiRoot(baseUrl), apiKey, modelName,
                configured ? "环境变量" : "未配置", capability, dimensions,
                configured, configured ? "environment" : "fallback");
    }

    static String environmentApiRoot(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return baseUrl;
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        int scheme = normalized.indexOf("://");
        int path = scheme < 0 ? -1 : normalized.indexOf('/', scheme + 3);
        return path < 0 ? normalized + "/v1" : normalized;
    }

    public record RuntimeModel(String baseUrl, String apiKey, String modelId, String providerName,
                               LanguageModel.Capability capability, Integer embeddingDimensions,
                               boolean configured, String source) { }
}
