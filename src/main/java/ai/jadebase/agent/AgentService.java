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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AgentService {

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
                .filter(agent -> canUse(agent.getCreatedBy(), agent.getAccessLevel(), actor))
                .map(agent -> runtimeView(runtime(agent.getId(), actor))).toList();
    }

    @Transactional
    public AgentView create(AgentInput input, JadeUser actor) {
        ValidatedInput value = validate(input);
        AgentDefinition agent = agents.save(new AgentDefinition(value.name(), value.description(),
                value.systemPrompt(), value.knowledgeBaseId(), value.modelProviderId(), value.modelId(),
                value.accessLevel(), value.thinkMode(), value.maxIterations(), actor.getId()));
        return view(agent);
    }

    @Transactional
    public AgentView update(UUID agentId, AgentInput input) {
        AgentDefinition agent = requireAgent(agentId);
        ValidatedInput value = validate(input);
        agent.update(value.name(), value.description(), value.systemPrompt(), value.knowledgeBaseId(),
                value.modelProviderId(), value.modelId(), value.accessLevel(), value.thinkMode(),
                value.maxIterations());
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
                version.getSystemPrompt(), version.getKnowledgeBaseId(), version.getModelProviderId(),
                version.getModelId(), version.isThinkMode(), version.getMaxIterations());
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

    private ValidatedInput validate(AgentInput input) {
        if (input == null) throw new IllegalArgumentException("Agent 配置不能为空");
        String name = required(input.name(), "名称", 120);
        String description = optional(input.description(), 500, "描述");
        String prompt = required(input.systemPrompt(), "系统提示词", 12000);
        UUID knowledgeBaseId = input.knowledgeBaseId();
        if (knowledgeBaseId == null || !knowledgeBases.existsById(knowledgeBaseId)) {
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
                ? AgentDefinition.AccessLevel.EVERYONE : input.accessLevel();
        int maxIterations = input.maxIterations() == null ? 4 : input.maxIterations();
        if (maxIterations < 1 || maxIterations > 12) throw new IllegalArgumentException("最大执行轮次必须在 1 到 12 之间");
        return new ValidatedInput(name, description, prompt, knowledgeBaseId, providerId, modelId,
                access, Boolean.TRUE.equals(input.thinkMode()), maxIterations);
    }

    private AgentView view(AgentDefinition agent) {
        KnowledgeBase knowledgeBase = knowledgeBases.findById(agent.getKnowledgeBaseId()).orElse(null);
        JadeUser creator = users.findById(agent.getCreatedBy()).orElse(null);
        String modelName = agent.getModelId();
        if (modelName == null) modelName = "工作区默认模型";
        else {
            ModelProvider provider = providers.findById(agent.getModelProviderId()).orElse(null);
            if (provider != null) modelName += " · " + provider.getDisplayName();
        }
        return new AgentView(agent.getId(), agent.getName(), agent.getDescription(), agent.getSystemPrompt(),
                agent.getKnowledgeBaseId(), knowledgeBase == null ? "已删除的知识库" : knowledgeBase.getName(),
                agent.getModelProviderId(), agent.getModelId(), modelName,
                agent.getAccessLevel().name().toLowerCase(Locale.ROOT),
                agent.getStatus().name().toLowerCase(Locale.ROOT), agent.isEnabled(), agent.isThinkMode(),
                agent.getMaxIterations(), agent.getCurrentVersion(),
                agent.getCurrentVersion() > 0 && agent.getStatus() == AgentDefinition.Status.DRAFT,
                agent.getCreatedBy(), creatorName(creator), agent.getPublishedAt(),
                agent.getCreatedAt(), agent.getUpdatedAt());
    }

    private AvailableAgentView runtimeView(RuntimeConfig value) {
        KnowledgeBase knowledgeBase = knowledgeBases.findById(value.knowledgeBaseId()).orElse(null);
        return new AvailableAgentView(value.id(), value.version(), value.name(), value.description(),
                value.knowledgeBaseId(), knowledgeBase == null ? "知识库" : knowledgeBase.getName(),
                value.modelId() == null ? "工作区默认模型" : value.modelId(), value.thinkMode());
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

    public record AgentInput(String name, String description, String systemPrompt, UUID knowledgeBaseId,
                             UUID modelProviderId, String modelId, AgentDefinition.AccessLevel accessLevel,
                             Boolean thinkMode, Integer maxIterations) { }
    private record ValidatedInput(String name, String description, String systemPrompt, UUID knowledgeBaseId,
                                  UUID modelProviderId, String modelId, AgentDefinition.AccessLevel accessLevel,
                                  boolean thinkMode, int maxIterations) { }
    public record AgentView(UUID id, String name, String description, String systemPrompt,
                            UUID knowledgeBaseId, String knowledgeBaseName, UUID modelProviderId,
                            String modelId, String modelName, String accessLevel, String status,
                            boolean enabled, boolean thinkMode, int maxIterations, int currentVersion,
                            boolean hasUnpublishedChanges, UUID createdBy, String createdByName,
                            Instant publishedAt, Instant createdAt, Instant updatedAt) { }
    public record AvailableAgentView(UUID id, int version, String name, String description,
                                     UUID knowledgeBaseId, String knowledgeBaseName,
                                     String modelName, boolean thinkMode) { }
    public record RuntimeConfig(UUID id, int version, String name, String description, String systemPrompt,
                                UUID knowledgeBaseId, UUID modelProviderId, String modelId,
                                boolean thinkMode, int maxIterations) { }
    public record VersionView(int version, String name, String description, String modelName,
                              String accessLevel, String publishedBy, Instant publishedAt) { }
    public record RunView(UUID id, int version, UUID conversationId, String status, String question,
                          String errorMessage, long durationMs, String user, Instant startedAt,
                          Instant completedAt) { }
}
