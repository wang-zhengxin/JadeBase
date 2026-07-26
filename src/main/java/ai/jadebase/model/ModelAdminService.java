package ai.jadebase.model;

import ai.jadebase.common.CredentialCipher;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ModelAdminService {

    private final ModelProviderRepository providers;
    private final LanguageModelRepository models;
    private final ModelProviderCatalog catalog;
    private final OpenAiProviderClient api;
    private final CredentialCipher cipher;
    private final ModelRuntimeResolver runtime;

    public ModelAdminService(ModelProviderRepository providers, LanguageModelRepository models,
                             ModelProviderCatalog catalog, OpenAiProviderClient api,
                             CredentialCipher cipher, ModelRuntimeResolver runtime) {
        this.providers = providers;
        this.models = models;
        this.catalog = catalog;
        this.api = api;
        this.cipher = cipher;
        this.runtime = runtime;
    }

    public List<ModelProviderCatalog.Preset> catalog() {
        return catalog.all();
    }

    @Transactional(readOnly = true)
    public List<ProviderView> list() {
        return providers.findAllByOrderByCreatedAtAsc().stream().map(this::view).toList();
    }

    public DiscoveryResult discover(ProviderConnection input) {
        ModelProviderCatalog.Preset preset = catalog.require(input.providerType());
        String key = key(input.apiKey(), preset.apiKeyRequired());
        List<String> discovered = api.discoverModels(baseUrl(input.baseUrl()), key);
        return new DiscoveryResult(discovered, "发现 " + discovered.size() + " 个模型");
    }

    public DiscoveryResult discoverExisting(UUID providerId, ProviderConnection input) {
        ModelProvider provider = requireProvider(providerId);
        String key = input.apiKey() == null || input.apiKey().isBlank()
                ? cipher.decrypt(provider.getEncryptedApiKey()) : key(input.apiKey(), false);
        String url = input.baseUrl() == null || input.baseUrl().isBlank()
                ? provider.getBaseUrl() : baseUrl(input.baseUrl());
        List<String> discovered = api.discoverModels(url, key);
        return new DiscoveryResult(discovered, "发现 " + discovered.size() + " 个模型");
    }

    public TestResult test(ProviderTest input) {
        ModelProvider existing = input.providerId() == null ? null : requireProvider(input.providerId());
        ModelProvider.Type type = input.providerType() == null && existing != null
                ? existing.getProviderType() : input.providerType();
        ModelProviderCatalog.Preset preset = catalog.require(type);
        String key = (input.apiKey() == null || input.apiKey().isBlank()) && existing != null
                ? cipher.decrypt(existing.getEncryptedApiKey()) : key(input.apiKey(), preset.apiKeyRequired());
        String modelId = required(input.modelId(), "模型 ID");
        api.testChat(baseUrl(input.baseUrl()), key, modelId);
        return new TestResult(true, "连接正常，模型 " + modelId + " 已返回响应");
    }

    @Transactional
    public ProviderView create(ProviderInput input) {
        ModelProviderCatalog.Preset preset = catalog.require(input.providerType());
        String key = key(input.apiKey(), preset.apiKeyRequired());
        List<String> selectedModels = modelIds(input.modelIds());
        String url = baseUrl(input.baseUrl());
        api.testChat(url, key, selectedModels.getFirst());
        ModelProvider provider = providers.save(new ModelProvider(input.providerType(),
                displayName(input.displayName(), preset.name()), url, encrypted(key)));
        selectedModels.forEach(id -> models.save(new LanguageModel(provider.getId(), id)));
        provider.connected("连接正常");
        providers.save(provider);
        ensureDefault();
        return view(provider);
    }

    @Transactional
    public ProviderView update(UUID providerId, ProviderInput input) {
        ModelProvider provider = requireProvider(providerId);
        ModelProviderCatalog.Preset preset = catalog.require(input.providerType());
        String key = input.apiKey() == null || input.apiKey().isBlank()
                ? cipher.decrypt(provider.getEncryptedApiKey()) : key(input.apiKey(), preset.apiKeyRequired());
        if (preset.apiKeyRequired() && key.isBlank()) throw new IllegalArgumentException("API Key 不能为空");
        List<String> selectedModels = modelIds(input.modelIds());
        String url = baseUrl(input.baseUrl());
        api.testChat(url, key, selectedModels.getFirst());
        String replacementKey = input.apiKey() == null || input.apiKey().isBlank() ? null : encrypted(key);
        provider.update(input.providerType(), displayName(input.displayName(), preset.name()), url, replacementKey);
        provider.connected("连接正常");
        syncModels(provider.getId(), selectedModels);
        providers.save(provider);
        ensureDefault();
        return view(provider);
    }

    @Transactional(noRollbackFor = ModelProviderException.class)
    public TestResult testExisting(UUID providerId) {
        ModelProvider provider = requireProvider(providerId);
        LanguageModel model = models.findByProviderIdOrderByCreatedAtAsc(providerId).stream()
                .filter(LanguageModel::isEnabled).findFirst()
                .orElseThrow(() -> new IllegalStateException("供应商没有可用模型"));
        try {
            api.testChat(provider.getBaseUrl(), cipher.decrypt(provider.getEncryptedApiKey()), model.getModelId());
            provider.connected("连接正常");
            providers.save(provider);
            return new TestResult(true, "连接正常，模型 " + model.getModelId() + " 已返回响应");
        } catch (ModelProviderException exception) {
            provider.failed(exception.getMessage());
            providers.save(provider);
            throw exception;
        }
    }

    @Transactional
    public CurrentModelView setDefault(DefaultModelInput input) {
        LanguageModel selected = models.findByProviderIdAndModelId(input.providerId(),
                        required(input.modelId(), "模型 ID"))
                .filter(LanguageModel::isEnabled)
                .orElseThrow(() -> new EntityNotFoundException("模型不存在或未启用"));
        List<LanguageModel> allModels = models.findAll();
        allModels.forEach(model -> model.setDefaultModel(model.getId().equals(selected.getId())));
        models.saveAll(allModels);
        return current();
    }

    @Transactional
    public void delete(UUID providerId) {
        ModelProvider provider = requireProvider(providerId);
        models.deleteAll(models.findByProviderIdOrderByCreatedAtAsc(providerId));
        providers.delete(provider);
        ensureDefault();
    }

    @Transactional(readOnly = true)
    public CurrentModelView current() {
        ModelRuntimeResolver.RuntimeModel model = runtime.current();
        UUID providerId = models.findFirstByDefaultModelTrueAndEnabledTrueOrderByCreatedAtAsc()
                .map(LanguageModel::getProviderId).orElse(null);
        return new CurrentModelView(providerId, model.modelId(), model.providerName(), model.configured(), model.source());
    }

    private void syncModels(UUID providerId, List<String> selected) {
        List<LanguageModel> existing = new ArrayList<>(models.findByProviderIdOrderByCreatedAtAsc(providerId));
        boolean removedDefault = existing.stream().anyMatch(item -> item.isDefaultModel() && !selected.contains(item.getModelId()));
        models.deleteAll(existing.stream().filter(item -> !selected.contains(item.getModelId())).toList());
        for (String modelId : selected) {
            LanguageModel model = existing.stream().filter(item -> item.getModelId().equals(modelId)).findFirst()
                    .orElseGet(() -> new LanguageModel(providerId, modelId));
            model.enable();
            models.save(model);
        }
        if (removedDefault) ensureDefault();
    }

    private void ensureDefault() {
        if (models.findFirstByDefaultModelTrueAndEnabledTrueOrderByCreatedAtAsc().isPresent()) return;
        models.findFirstByEnabledTrueOrderByCreatedAtAsc().ifPresent(model -> {
            model.setDefaultModel(true);
            models.save(model);
        });
    }

    private ProviderView view(ModelProvider provider) {
        List<ModelView> availableModels = models.findByProviderIdOrderByCreatedAtAsc(provider.getId()).stream()
                .map(item -> new ModelView(item.getId(), item.getModelId(), item.getDisplayName(),
                        item.isEnabled(), item.isDefaultModel())).toList();
        return new ProviderView(provider.getId(), provider.getProviderType(), provider.getDisplayName(),
                provider.getBaseUrl(), provider.getEncryptedApiKey() != null && !provider.getEncryptedApiKey().isBlank(),
                provider.getStatus(), provider.getStatusMessage(), provider.getLastTestedAt(), availableModels);
    }

    private ModelProvider requireProvider(UUID id) {
        return providers.findById(id).orElseThrow(() -> new EntityNotFoundException("模型供应商不存在"));
    }

    private String displayName(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        if (result.length() > 120) throw new IllegalArgumentException("显示名称不能超过 120 个字符");
        return result;
    }

    private String baseUrl(String value) {
        String result = required(value, "API Base URL");
        if (result.length() > 500) throw new IllegalArgumentException("API Base URL 不能超过 500 个字符");
        URI uri;
        try { uri = URI.create(result); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("API Base URL 格式无效"); }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("API Base URL 必须是有效的 HTTP 或 HTTPS 地址");
        }
        return result.endsWith("/") ? result.substring(0, result.length() - 1) : result;
    }

    private String key(String value, boolean required) {
        String result = value == null ? "" : value.trim();
        if (required && result.isBlank()) throw new IllegalArgumentException("API Key 不能为空");
        if (result.length() > 1000) throw new IllegalArgumentException("API Key 长度无效");
        return result;
    }

    private String encrypted(String key) {
        return key.isBlank() ? null : cipher.encrypt(key);
    }

    private List<String> modelIds(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) values.forEach(value -> {
            if (value != null && !value.isBlank()) {
                String id = value.trim();
                if (id.length() > 255) throw new IllegalArgumentException("模型 ID 不能超过 255 个字符");
                result.add(id);
            }
        });
        if (result.isEmpty()) throw new IllegalArgumentException("请至少选择或填写一个模型");
        if (result.size() > 100) throw new IllegalArgumentException("单个供应商最多启用 100 个模型");
        return List.copyOf(result);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return value.trim();
    }

    public record ProviderConnection(ModelProvider.Type providerType, String baseUrl, String apiKey) { }
    public record ProviderTest(UUID providerId, ModelProvider.Type providerType, String baseUrl,
                               String apiKey, String modelId) { }
    public record ProviderInput(ModelProvider.Type providerType, String displayName, String baseUrl,
                                String apiKey, List<String> modelIds) { }
    public record DefaultModelInput(UUID providerId, String modelId) { }
    public record DiscoveryResult(List<String> models, String message) { }
    public record TestResult(boolean connected, String message) { }
    public record ModelView(UUID id, String modelId, String displayName, boolean enabled, boolean defaultModel) { }
    public record ProviderView(UUID id, ModelProvider.Type providerType, String displayName, String baseUrl,
                               boolean apiKeyConfigured, ModelProvider.Status status, String statusMessage,
                               Instant lastTestedAt, List<ModelView> models) { }
    public record CurrentModelView(UUID providerId, String modelId, String providerName,
                                   boolean configured, String source) { }
}
