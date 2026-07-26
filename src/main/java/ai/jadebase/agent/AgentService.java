package ai.jadebase.agent;

import ai.jadebase.identity.domain.IdentityAccessException;
import ai.jadebase.identity.domain.JadeUser;
import ai.jadebase.identity.infra.JadeUserRepository;
import ai.jadebase.knowledge.domain.KnowledgeBase;
import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import ai.jadebase.model.LanguageModel;
import ai.jadebase.model.LanguageModelRepository;
import ai.jadebase.model.ModelProvider;
import ai.jadebase.model.ModelProviderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentService {

    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            "IMAGE_GENERATION", "WEB_SEARCH", "OPEN_URL", "CODE_INTERPRETER", "CODING_AGENT");

    private final AgentDefinitionRepository agents;
    private final AgentVersionRepository versions;
    private final AgentRunRepository runs;
    private final KnowledgeBaseRepository knowledgeBases;
    private final ModelProviderRepository providers;
    private final LanguageModelRepository models;
    private final JadeUserRepository users;

    public AgentService(AgentDefinitionRepository agents, AgentVersionRepository versions,
                        AgentRunRepository runs, KnowledgeBaseRepository knowledgeBases,
                        ModelProviderRepository providers, LanguageModelRepository models,
                        JadeUserRepository users) {
        this.agents = agents;
        this.versions = versions;
        this.runs = runs;
        this.knowledgeBases = knowledgeBases;
        this.providers = providers;
        this.models = models;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AgentView> listAdmin(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return agents.findAllByOrderByUpdatedAtDesc().stream()
                .filter(agent -> normalized.isBlank()
                        || agent.getName().toLowerCase(Locale.ROOT).contains(normalized)
                        || text(agent.getDescription()).toLowerCase(Locale.ROOT).contains(normalized))
                .map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public AgentView getAdmin(UUID agentId) {
        return view(requireAgent(agentId));
    }

    @Transactional(readOnly = true)
    public List<AvailableAgentView> listAvailable(JadeUser actor) {
        return agents.findAllByOrderByUpdatedAtDesc().stream()
                .filter(agent -> agent.isEnabled() && agent.getCurrentVersion() > 0)
                .filter(agent -> canUsePublishedVersion(agent, actor))
                .map(agent -> runtimeView(runtime(agent.getId(), actor)))
                .sorted((left, right) -> Boolean.compare(right.featured(), left.featured())).toList();
    }

    @Transactional
    public AgentView create(AgentInput input, JadeUser actor) {
        AgentDefinition.Configuration value = validate(input);
        AgentDefinition agent = agents.save(new AgentDefinition(value, actor.getId()));
        return view(agent);
    }

    @Transactional
    public AgentView update(UUID agentId, AgentInput input) {
        AgentDefinition agent = requireAgent(agentId);
        AgentDefinition.Configuration value = validate(input);
        agent.update(value);
        return view(agents.save(agent));
    }

    @Transactional
    public AgentView publish(UUID agentId, JadeUser actor) {
        AgentDefinition agent = requireAgent(agentId);
        int nextVersion = agent.getCurrentVersion() + 1;
        versions.save(new AgentVersion(agent, nextVersion, actor.getId()));
        agent.publish(nextVersion);
        return view(agents.save(agent));
    }

    @Transactional
    public AgentView setEnabled(UUID agentId, boolean enabled) {
        AgentDefinition agent = requireAgent(agentId);
        if (enabled && agent.getCurrentVersion() == 0) {
            throw new IllegalStateException("请先发布 Agent，再启用运行");
        }
        agent.setEnabled(enabled);
        return view(agents.save(agent));
    }

    @Transactional
    public void delete(UUID agentId) {
        agents.delete(requireAgent(agentId));
    }

    @Transactional(readOnly = true)
    public List<VersionView> versions(UUID agentId) {
        AgentDefinition agent = requireAgent(agentId);
        return java.util.stream.IntStream.rangeClosed(1, agent.getCurrentVersion())
                .mapToObj(number -> versions.findByAgentIdAndVersion(agentId, number).orElse(null))
                .filter(java.util.Objects::nonNull).map(this::versionView).toList().reversed();
    }

    @Transactional(readOnly = true)
    public List<RunView> runs(UUID agentId) {
        requireAgent(agentId);
        return runs.findTop20ByAgentIdOrderByStartedAtDesc(agentId).stream().map(this::runView).toList();
    }

    @Transactional(readOnly = true)
    public RuntimeConfig runtime(UUID agentId, JadeUser actor) {
        AgentDefinition agent = requireAgent(agentId);
        if (!agent.isEnabled() || agent.getCurrentVersion() == 0) {
            throw new IllegalStateException("Agent 尚未发布或已停用");
        }
        AgentVersion version = versions.findByAgentIdAndVersion(agentId, agent.getCurrentVersion())
                .orElseThrow(() -> new IllegalStateException("Agent 发布版本不存在"));
        if (!canUse(agent.getCreatedBy(), version.getAccessLevel(), actor)) {
            throw new IdentityAccessException("你没有权限使用这个 Agent");
        }
        return new RuntimeConfig(agentId, version.getVersion(), version.getName(), version.getDescription(),
                version.getSystemPrompt(), AgentConfigJson.read(version.getConversationStartersJson()),
                version.isUseKnowledge(), version.getKnowledgeBaseId(), version.getModelProviderId(),
                version.getModelId(), version.isThinkMode(), version.getMaxIterations(), version.isFeatured(),
                AgentConfigJson.read(version.getLabelsJson()), AgentConfigJson.read(version.getEnabledActionsJson()),
                version.getKnowledgeCutoffDate());
    }

    @Transactional
    public UUID startRun(RuntimeConfig runtime, JadeUser actor, String question) {
        return runs.save(new AgentRun(runtime.id(), runtime.version(), actor.getId(), question)).getId();
    }

    @Transactional
    public void completeRun(UUID runId, UUID conversationId, String answer) {
        AgentRun run = requireRun(runId);
        run.complete(conversationId, answer);
        runs.save(run);
    }

    @Transactional
    public void failRun(UUID runId, Throwable error) {
        AgentRun run = requireRun(runId);
        run.fail(error == null ? null : error.getMessage());
        runs.save(run);
    }

    private AgentDefinition.Configuration validate(AgentInput input) {
        if (input == null) throw new IllegalArgumentException("Agent 配置不能为空");
        String name = required(input.name(), "名称", 120);
        String description = optional(input.description(), 500, "描述");
        String prompt = optional(input.systemPrompt(), 12000, "系统提示词");
        if (prompt == null) prompt = "";
        List<String> starters = stringList(input.conversationStarters(), 6, 240, "对话开场白");
        boolean useKnowledge = input.useKnowledge() == null
                ? input.knowledgeBaseId() != null : input.useKnowledge();
        UUID knowledgeBaseId = useKnowledge ? input.knowledgeBaseId() : null;
        if (useKnowledge && (knowledgeBaseId == null || !knowledgeBases.existsById(knowledgeBaseId))) {
            throw new IllegalArgumentException("请选择有效的知识库");
        }
        UUID providerId = input.modelProviderId();
        String modelId = optional(input.modelId(), 255, "模型 ID");
        if (providerId == null && modelId != null) throw new IllegalArgumentException("模型供应商不能为空");
        if (providerId != null) {
            if (modelId == null) throw new IllegalArgumentException("请选择 Agent 使用的模型");
            LanguageModel model = models.findByProviderIdAndModelId(providerId, modelId)
                    .filter(LanguageModel::isEnabled)
                    .orElseThrow(() -> new IllegalArgumentException("选择的模型不存在或未启用"));
            if (!providers.existsById(model.getProviderId())) throw new IllegalArgumentException("模型供应商不存在");
        }
        AgentDefinition.AccessLevel access = input.accessLevel() == null
                ? AgentDefinition.AccessLevel.PRIVATE : input.accessLevel();
        int maxIterations = input.maxIterations() == null ? 4 : input.maxIterations();
        if (maxIterations < 1 || maxIterations > 12) throw new IllegalArgumentException("最大执行轮次必须在 1 到 12 之间");
        List<String> labels = stringList(input.labels(), 8, 40, "标签");
        List<String> actions = stringList(input.enabledActions(), SUPPORTED_ACTIONS.size(), 40, "Action");
        if (!SUPPORTED_ACTIONS.containsAll(actions)) throw new IllegalArgumentException("包含不支持的 Agent Action");
        boolean featured = Boolean.TRUE.equals(input.featured()) && access == AgentDefinition.AccessLevel.EVERYONE;
        LocalDate cutoff = input.knowledgeCutoffDate();
        if (cutoff != null && cutoff.isAfter(LocalDate.now())) throw new IllegalArgumentException("知识截止日期不能晚于今天");
        return new AgentDefinition.Configuration(name, description, prompt, AgentConfigJson.write(starters),
                useKnowledge, knowledgeBaseId, providerId, modelId, access,
                Boolean.TRUE.equals(input.thinkMode()), maxIterations, featured,
                AgentConfigJson.write(labels), AgentConfigJson.write(actions), cutoff);
    }

    private AgentView view(AgentDefinition agent) {
        KnowledgeBase knowledgeBase = agent.getKnowledgeBaseId() == null
                ? null : knowledgeBases.findById(agent.getKnowledgeBaseId()).orElse(null);
        JadeUser creator = users.findById(agent.getCreatedBy()).orElse(null);
        String modelName = agent.getModelId();
        if (modelName == null) modelName = "工作区默认模型";
        else {
            ModelProvider provider = providers.findById(agent.getModelProviderId()).orElse(null);
            if (provider != null) modelName += " · " + provider.getDisplayName();
        }
        return new AgentView(agent.getId(), agent.getName(), agent.getDescription(), agent.getSystemPrompt(),
                AgentConfigJson.read(agent.getConversationStartersJson()), agent.isUseKnowledge(),
                agent.getKnowledgeBaseId(), knowledgeBaseName(agent.isUseKnowledge(), knowledgeBase),
                agent.getModelProviderId(), agent.getModelId(), modelName,
                agent.getAccessLevel().name().toLowerCase(Locale.ROOT),
                agent.getStatus().name().toLowerCase(Locale.ROOT), agent.isEnabled(), agent.isThinkMode(),
                agent.getMaxIterations(), agent.isFeatured(), AgentConfigJson.read(agent.getLabelsJson()),
                AgentConfigJson.read(agent.getEnabledActionsJson()), agent.getKnowledgeCutoffDate(), agent.getCurrentVersion(),
                agent.getCurrentVersion() > 0 && agent.getStatus() == AgentDefinition.Status.DRAFT,
                agent.getCreatedBy(), creatorName(creator), agent.getPublishedAt(),
                agent.getCreatedAt(), agent.getUpdatedAt());
    }

    private AvailableAgentView runtimeView(RuntimeConfig value) {
        KnowledgeBase knowledgeBase = value.knowledgeBaseId() == null
                ? null : knowledgeBases.findById(value.knowledgeBaseId()).orElse(null);
        return new AvailableAgentView(value.id(), value.version(), value.name(), value.description(),
                value.conversationStarters(), value.useKnowledge(), value.knowledgeBaseId(),
                knowledgeBaseName(value.useKnowledge(), knowledgeBase),
                value.modelId() == null ? "工作区默认模型" : value.modelId(), value.thinkMode(),
                value.featured(), value.labels(), value.enabledActions());
    }

    private VersionView versionView(AgentVersion version) {
        JadeUser publisher = users.findById(version.getPublishedBy()).orElse(null);
        return new VersionView(version.getVersion(), version.getName(), version.getDescription(),
                version.getModelId() == null ? "工作区默认模型" : version.getModelId(),
                version.getAccessLevel().name().toLowerCase(Locale.ROOT), creatorName(publisher),
                version.getPublishedAt());
    }

    private RunView runView(AgentRun run) {
        JadeUser user = users.findById(run.getUserId()).orElse(null);
        return new RunView(run.getId(), run.getAgentVersion(), run.getConversationId(),
                run.getStatus().name().toLowerCase(Locale.ROOT), run.getQuestion(), run.getErrorMessage(),
                run.getDurationMs(), creatorName(user), run.getStartedAt(), run.getCompletedAt());
    }

    private boolean canUse(UUID createdBy, AgentDefinition.AccessLevel access, JadeUser actor) {
        return access == AgentDefinition.AccessLevel.EVERYONE || createdBy.equals(actor.getId())
                || actor.getRole() == JadeUser.Role.OWNER;
    }

    private boolean canUsePublishedVersion(AgentDefinition agent, JadeUser actor) {
        return versions.findByAgentIdAndVersion(agent.getId(), agent.getCurrentVersion())
                .map(version -> canUse(agent.getCreatedBy(), version.getAccessLevel(), actor))
                .orElse(false);
    }

    private AgentDefinition requireAgent(UUID id) {
        return agents.findById(id).orElseThrow(() -> new EntityNotFoundException("Agent 不存在"));
    }

    private AgentRun requireRun(UUID id) {
        return runs.findById(id).orElseThrow(() -> new EntityNotFoundException("Agent 运行记录不存在"));
    }

    private String required(String value, String label, int max) {
        String result = value == null ? "" : value.trim();
        if (result.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        if (result.length() > max) throw new IllegalArgumentException(label + "不能超过 " + max + " 个字符");
        return result;
    }

    private String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException(label + "不能超过 " + max + " 个字符");
        return result;
    }

    private String creatorName(JadeUser user) {
        if (user == null) return "未知用户";
        return user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getEmail() : user.getDisplayName();
    }

    private String text(String value) { return value == null ? "" : value; }

    private List<String> stringList(List<String> values, int maxItems, int maxLength, String label) {
        if (values == null) return List.of();
        List<String> result = values.stream().map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().toList();
        if (result.size() > maxItems) throw new IllegalArgumentException(label + "最多允许 " + maxItems + " 项");
        if (result.stream().anyMatch(value -> value.length() > maxLength)) {
            throw new IllegalArgumentException(label + "单项不能超过 " + maxLength + " 个字符");
        }
        return result;
    }

    private String knowledgeBaseName(boolean useKnowledge, KnowledgeBase knowledgeBase) {
        if (!useKnowledge) return "未使用知识库";
        return knowledgeBase == null ? "已删除的知识库" : knowledgeBase.getName();
    }

    public record AgentInput(String name, String description, String systemPrompt, UUID knowledgeBaseId,
                             UUID modelProviderId, String modelId, AgentDefinition.AccessLevel accessLevel,
                             Boolean thinkMode, Integer maxIterations, List<String> conversationStarters,
                             Boolean useKnowledge, Boolean featured, List<String> labels,
                             List<String> enabledActions, LocalDate knowledgeCutoffDate) { }
    public record AgentView(UUID id, String name, String description, String systemPrompt,
                            List<String> conversationStarters, boolean useKnowledge,
                            UUID knowledgeBaseId, String knowledgeBaseName, UUID modelProviderId,
                            String modelId, String modelName, String accessLevel, String status,
                            boolean enabled, boolean thinkMode, int maxIterations, boolean featured,
                            List<String> labels, List<String> enabledActions, LocalDate knowledgeCutoffDate,
                            int currentVersion,
                            boolean hasUnpublishedChanges, UUID createdBy, String createdByName,
                            Instant publishedAt, Instant createdAt, Instant updatedAt) { }
    public record AvailableAgentView(UUID id, int version, String name, String description,
                                     List<String> conversationStarters, boolean useKnowledge,
                                     UUID knowledgeBaseId, String knowledgeBaseName,
                                     String modelName, boolean thinkMode, boolean featured,
                                     List<String> labels, List<String> enabledActions) { }
    public record RuntimeConfig(UUID id, int version, String name, String description, String systemPrompt,
                                List<String> conversationStarters, boolean useKnowledge,
                                UUID knowledgeBaseId, UUID modelProviderId, String modelId,
                                boolean thinkMode, int maxIterations, boolean featured,
                                List<String> labels, List<String> enabledActions,
                                LocalDate knowledgeCutoffDate) { }
    public record VersionView(int version, String name, String description, String modelName,
                              String accessLevel, String publishedBy, Instant publishedAt) { }
    public record RunView(UUID id, int version, UUID conversationId, String status, String question,
                          String errorMessage, long durationMs, String user, Instant startedAt,
                          Instant completedAt) { }
}
