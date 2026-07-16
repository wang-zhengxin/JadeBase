package ai.jadebase.connector.feishu;

import ai.jadebase.knowledge.domain.KnowledgeBase;
import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FeishuConnectorService {

    private final FeishuConnectionRepository connections;
    private final FeishuSourceRepository sources;
    private final FeishuRemoteDocumentRepository remoteDocuments;
    private final FeishuSyncTaskRepository tasks;
    private final KnowledgeBaseRepository knowledgeBases;
    private final ai.jadebase.knowledge.domain.KnowledgeBaseService knowledgeBaseService;
    private final FeishuApiClient api;
    private final ConnectorSecretCipher cipher;
    private final FeishuSyncQueue queue;
    private final FeishuConnectorProperties properties;

    public FeishuConnectorService(FeishuConnectionRepository connections, FeishuSourceRepository sources,
                                  FeishuRemoteDocumentRepository remoteDocuments,
                                  FeishuSyncTaskRepository tasks, KnowledgeBaseRepository knowledgeBases,
                                  ai.jadebase.knowledge.domain.KnowledgeBaseService knowledgeBaseService,
                                  FeishuApiClient api, ConnectorSecretCipher cipher, FeishuSyncQueue queue,
                                  FeishuConnectorProperties properties) {
        this.connections = connections;
        this.sources = sources;
        this.remoteDocuments = remoteDocuments;
        this.tasks = tasks;
        this.knowledgeBases = knowledgeBases;
        this.knowledgeBaseService = knowledgeBaseService;
        this.api = api;
        this.cipher = cipher;
        this.queue = queue;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<ConnectionView> listConnections() {
        return connections.findAll().stream().map(this::view).toList();
    }

    public ConnectionTest test(ConnectionInput input) {
        String apiBaseUrl = apiBaseUrl(input.apiBaseUrl());
        FeishuConnection candidate = new FeishuConnection(name(input.name()), required(input.appId(), "App ID"),
                cipher.encrypt(required(input.appSecret(), "App Secret")), apiBaseUrl, webBaseUrl(input.webBaseUrl()));
        api.authenticate(candidate, input.appSecret());
        return new ConnectionTest(true, "凭证有效，已获取 tenant_access_token");
    }

    @Transactional
    public ConnectionView create(ConnectionInput input) {
        String appSecret = required(input.appSecret(), "App Secret");
        FeishuConnection connection = new FeishuConnection(name(input.name()), required(input.appId(), "App ID"),
                cipher.encrypt(appSecret), apiBaseUrl(input.apiBaseUrl()), webBaseUrl(input.webBaseUrl()));
        api.authenticate(connection, appSecret);
        return view(connections.save(connection));
    }

    @Transactional(noRollbackFor = FeishuApiException.class)
    public ConnectionView update(UUID id, ConnectionInput input) {
        FeishuConnection connection = requireConnection(id);
        String appSecret = input.appSecret() == null || input.appSecret().isBlank()
                ? cipher.decrypt(connection.getEncryptedAppSecret()) : input.appSecret().trim();
        String encrypted = input.appSecret() == null || input.appSecret().isBlank()
                ? null : cipher.encrypt(appSecret);
        connection.update(name(input.name()), required(input.appId(), "App ID"), encrypted,
                apiBaseUrl(input.apiBaseUrl()), webBaseUrl(input.webBaseUrl()));
        try {
            api.authenticate(connection, appSecret);
            connection.connected();
        } catch (FeishuApiException exception) {
            connection.failed(exception.getMessage());
            connections.save(connection);
            throw exception;
        }
        return view(connections.save(connection));
    }

    public ConnectionTest testExisting(UUID id) {
        FeishuConnection connection = requireConnection(id);
        api.authenticate(connection, cipher.decrypt(connection.getEncryptedAppSecret()));
        connection.connected();
        connections.save(connection);
        return new ConnectionTest(true, "连接正常");
    }

    public List<FeishuApiClient.RemoteContainer> spaces(UUID connectionId) {
        FeishuConnection connection = requireConnection(connectionId);
        String token = token(connection);
        List<FeishuApiClient.RemoteContainer> result = new ArrayList<>();
        String cursor = null;
        do {
            FeishuApiClient.Page<FeishuApiClient.RemoteContainer> page = api.listWikiSpaces(connection, token, cursor);
            result.addAll(page.items());
            cursor = page.hasMore() ? page.nextPageToken() : null;
        } while (cursor != null && !cursor.isBlank());
        return result;
    }

    public FolderBrowse folders(UUID connectionId, String folderToken) {
        FeishuConnection connection = requireConnection(connectionId);
        String token = token(connection);
        FeishuApiClient.RemoteContainer root = folderToken == null || folderToken.isBlank()
                ? api.getRootFolder(connection, token)
                : new FeishuApiClient.RemoteContainer(folderToken.trim(), "所选文件夹", FeishuSource.Type.FOLDER);
        List<FeishuApiClient.RemoteItem> result = new ArrayList<>();
        String cursor = null;
        do {
            FeishuApiClient.Page<FeishuApiClient.RemoteItem> page = api.listFolderItems(
                    connection, token, root.id(), cursor);
            result.addAll(page.items());
            cursor = page.hasMore() ? page.nextPageToken() : null;
        } while (cursor != null && !cursor.isBlank());
        return new FolderBrowse(root, result);
    }

    @Transactional(readOnly = true)
    public List<SourceView> listSources() {
        return sources.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional
    public SourceView createSource(SourceInput input) {
        requireConnection(input.connectionId());
        if (input.sourceType() == null) throw new IllegalArgumentException("飞书来源类型不能为空");
        KnowledgeBase knowledgeBase = knowledgeBases.findById(input.knowledgeBaseId())
                .orElseThrow(() -> new EntityNotFoundException("知识库不存在"));
        String remoteId = required(input.remoteId(), "飞书来源 ID");
        String remoteName = input.remoteName() == null || input.remoteName().isBlank()
                ? remoteId : input.remoteName().trim();
        FeishuSource source = sources.findByConnectionIdAndSourceTypeAndRemoteIdAndKnowledgeBaseId(
                        input.connectionId(), input.sourceType(), remoteId, knowledgeBase.getId())
                .orElseGet(() -> sources.save(new FeishuSource(input.connectionId(), knowledgeBase.getId(),
                        input.sourceType(), remoteId, remoteName, interval(input.syncIntervalMinutes()))));
        queue.enqueue(source.getId(), FeishuSyncTask.Mode.FULL);
        return view(source);
    }

    @Transactional
    public SourceView setEnabled(UUID sourceId, boolean enabled) {
        FeishuSource source = requireSource(sourceId);
        source.setEnabled(enabled);
        return view(sources.save(source));
    }

    @Transactional
    public void deleteSource(UUID sourceId) {
        FeishuSource source = requireSource(sourceId);
        if (tasks.existsBySourceIdAndStatusIn(sourceId, List.of(
                FeishuSyncTask.Status.QUEUED, FeishuSyncTask.Status.RUNNING))) {
            throw new IllegalStateException("来源正在同步，请等待任务结束后再删除");
        }
        for (FeishuRemoteDocument item : remoteDocuments.findBySourceId(sourceId)) {
            knowledgeBaseService.deleteDocument(source.getKnowledgeBaseId(), item.getDocumentId());
        }
        sources.delete(source);
    }

    @Transactional
    public void deleteConnection(UUID connectionId) {
        FeishuConnection connection = requireConnection(connectionId);
        List<FeishuSource> linked = sources.findAll().stream()
                .filter(source -> source.getConnectionId().equals(connectionId)).toList();
        linked.forEach(source -> deleteSource(source.getId()));
        connections.delete(connection);
    }

    public TaskView sync(UUID sourceId, FeishuSyncTask.Mode mode) {
        return taskView(queue.enqueue(sourceId, mode));
    }

    public TaskView retry(UUID taskId) {
        return taskView(queue.retry(taskId));
    }

    @Transactional(readOnly = true)
    public List<TaskView> tasks() {
        return tasks.findTop20ByOrderByCreatedAtDesc().stream().map(this::taskView).toList();
    }

    private String token(FeishuConnection connection) {
        return api.authenticate(connection, cipher.decrypt(connection.getEncryptedAppSecret()));
    }

    private ConnectionView view(FeishuConnection connection) {
        long sourceCount = sources.findAll().stream()
                .filter(source -> source.getConnectionId().equals(connection.getId())).count();
        return new ConnectionView(connection.getId(), connection.getName(), connection.getAppId(),
                connection.getApiBaseUrl(), connection.getWebBaseUrl(), connection.getStatus(),
                connection.getStatusMessage(), connection.getLastConnectedAt(), sourceCount);
    }

    private SourceView view(FeishuSource source) {
        String knowledgeBaseName = knowledgeBases.findById(source.getKnowledgeBaseId())
                .map(KnowledgeBase::getName).orElse("已删除知识库");
        TaskView latest = tasks.findTopBySourceIdOrderByCreatedAtDesc(source.getId())
                .map(this::taskView).orElse(null);
        return new SourceView(source.getId(), source.getConnectionId(), source.getKnowledgeBaseId(),
                knowledgeBaseName, source.getSourceType(), source.getRemoteId(), source.getRemoteName(),
                source.isEnabled(), source.getSyncIntervalMinutes(), source.getLastSyncStartedAt(),
                source.getLastSyncCompletedAt(), source.getLastSyncStatus(), source.getLastSyncMessage(), latest);
    }

    private TaskView taskView(FeishuSyncTask task) {
        return new TaskView(task.getId(), task.getSourceId(), task.getMode(), task.getStatus(),
                task.getScannedCount(), task.getCreatedCount(), task.getUpdatedCount(), task.getDeletedCount(),
                task.getSkippedCount(), task.getAttemptCount(), task.getErrorMessage(), task.getCreatedAt(),
                task.getStartedAt(), task.getCompletedAt());
    }

    private FeishuConnection requireConnection(UUID id) {
        return connections.findById(id).orElseThrow(() -> new EntityNotFoundException("飞书连接不存在"));
    }

    private FeishuSource requireSource(UUID id) {
        return sources.findById(id).orElseThrow(() -> new EntityNotFoundException("飞书同步来源不存在"));
    }

    private int interval(Integer value) {
        return value == null ? properties.syncIntervalMinutes() : Math.max(5, Math.min(1440, value));
    }

    private String name(String value) {
        return value == null || value.isBlank() ? "飞书企业连接" : value.trim();
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " 不能为空");
        return value.trim();
    }

    private String apiBaseUrl(String value) {
        String configured = value == null || value.isBlank() ? properties.apiBaseUrl() : value.trim();
        validateBaseUrl(configured);
        if (!properties.allowCustomBaseUrl() && !configured.equals(properties.apiBaseUrl())) {
            throw new IllegalArgumentException("不允许使用自定义飞书 API 地址");
        }
        return stripTrailingSlash(configured);
    }

    private String webBaseUrl(String value) {
        String configured = value == null || value.isBlank() ? properties.webBaseUrl() : value.trim();
        validateBaseUrl(configured);
        return stripTrailingSlash(configured);
    }

    private void validateBaseUrl(String value) {
        URI uri;
        try { uri = URI.create(value); }
        catch (Exception exception) { throw new IllegalArgumentException("飞书地址格式无效"); }
        if (uri.getHost() == null || !("https".equals(uri.getScheme())
                || properties.allowCustomBaseUrl() && "http".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("飞书地址必须使用 HTTPS");
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record ConnectionInput(String name, String appId, String appSecret,
                                  String apiBaseUrl, String webBaseUrl) { }
    public record ConnectionTest(boolean connected, String message) { }
    public record ConnectionView(UUID id, String name, String appId, String apiBaseUrl, String webBaseUrl,
                                 FeishuConnection.Status status, String statusMessage, Instant lastConnectedAt,
                                 long sourceCount) { }
    public record SourceInput(UUID connectionId, UUID knowledgeBaseId, FeishuSource.Type sourceType,
                              String remoteId, String remoteName, Integer syncIntervalMinutes) { }
    public record SourceView(UUID id, UUID connectionId, UUID knowledgeBaseId, String knowledgeBaseName,
                             FeishuSource.Type sourceType, String remoteId, String remoteName, boolean enabled,
                             int syncIntervalMinutes, Instant lastSyncStartedAt, Instant lastSyncCompletedAt,
                             String lastSyncStatus, String lastSyncMessage, TaskView latestTask) { }
    public record TaskView(UUID id, UUID sourceId, FeishuSyncTask.Mode mode, FeishuSyncTask.Status status,
                           int scannedCount, int createdCount, int updatedCount, int deletedCount,
                           int skippedCount, int attemptCount, String errorMessage, Instant createdAt,
                           Instant startedAt, Instant completedAt) { }
    public record FolderBrowse(FeishuApiClient.RemoteContainer folder, List<FeishuApiClient.RemoteItem> items) { }
}
