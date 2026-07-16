package ai.jadebase.connector.feishu;

import ai.jadebase.knowledge.domain.Document;
import ai.jadebase.knowledge.domain.KnowledgeBaseService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FeishuSyncStateService {

    private final FeishuSyncTaskRepository tasks;
    private final FeishuSourceRepository sources;
    private final FeishuConnectionRepository connections;
    private final FeishuRemoteDocumentRepository remoteDocuments;
    private final KnowledgeBaseService knowledgeBases;
    private final FeishuConnectorProperties properties;

    public FeishuSyncStateService(FeishuSyncTaskRepository tasks, FeishuSourceRepository sources,
                                  FeishuConnectionRepository connections,
                                  FeishuRemoteDocumentRepository remoteDocuments,
                                  KnowledgeBaseService knowledgeBases, FeishuConnectorProperties properties) {
        this.tasks = tasks;
        this.sources = sources;
        this.connections = connections;
        this.remoteDocuments = remoteDocuments;
        this.knowledgeBases = knowledgeBases;
        this.properties = properties;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public SyncContext context(UUID taskId) {
        FeishuSyncTask task = requireTask(taskId);
        FeishuSource source = sources.findById(task.getSourceId())
                .orElseThrow(() -> new EntityNotFoundException("飞书同步来源不存在"));
        FeishuConnection connection = connections.findById(source.getConnectionId())
                .orElseThrow(() -> new EntityNotFoundException("飞书连接不存在"));
        return new SyncContext(task, source, connection);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<RemoteSnapshot> remote(UUID sourceId, String remoteToken) {
        return remoteDocuments.findBySourceIdAndRemoteToken(sourceId, remoteToken)
                .map(item -> new RemoteSnapshot(item.getDocumentId(), item.getRemoteRevision(),
                        item.getRemoteUpdatedAt(), item.getContentHash()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSeen(UUID taskId, FeishuSource source, FeishuApiClient.RemoteItem item) {
        FeishuRemoteDocument remote = remoteDocuments.findBySourceIdAndRemoteToken(source.getId(), item.objectToken())
                .orElseThrow(() -> new EntityNotFoundException("飞书远程文档映射不存在"));
        remote.seen(item, taskId);
        remoteDocuments.save(remote);
        knowledgeBases.updateRemoteMetadata(source.getKnowledgeBaseId(), remote.getDocumentId(),
                new KnowledgeBaseService.RemoteDocumentMetadata(title(item), item.objectToken(), item.sourceUrl(),
                        item.author(), item.updatedAt()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnchangedContent(UUID taskId, FeishuSource source, FeishuApiClient.RemoteItem item,
                                     String revision, String contentHash) {
        FeishuRemoteDocument remote = remoteDocuments.findBySourceIdAndRemoteToken(source.getId(), item.objectToken())
                .orElseThrow(() -> new EntityNotFoundException("飞书远程文档映射不存在"));
        remote.update(item, revision, contentHash, taskId);
        remoteDocuments.save(remote);
        knowledgeBases.updateRemoteMetadata(source.getKnowledgeBaseId(), remote.getDocumentId(),
                new KnowledgeBaseService.RemoteDocumentMetadata(title(item), item.objectToken(), item.sourceUrl(),
                        item.author(), item.updatedAt()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Change upsert(UUID taskId, FeishuSource source, FeishuApiClient.RemoteItem item,
                         String revision, String contentHash, String content) {
        Optional<FeishuRemoteDocument> existing = remoteDocuments
                .findBySourceIdAndRemoteToken(source.getId(), item.objectToken());
        Document document = knowledgeBases.upsertRemoteDocument(source.getKnowledgeBaseId(),
                existing.map(FeishuRemoteDocument::getDocumentId).orElse(null),
                new KnowledgeBaseService.RemoteDocument(title(item), item.objectToken(), item.sourceUrl(),
                        item.author(), item.updatedAt(), content));
        if (existing.isPresent()) {
            existing.get().update(item, revision, contentHash, taskId);
            remoteDocuments.save(existing.get());
            return Change.UPDATED;
        }
        remoteDocuments.save(new FeishuRemoteDocument(source.getId(), document.getId(), item,
                revision, contentHash, taskId));
        return Change.CREATED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void heartbeat(UUID taskId, String cursorJson, Counts counts) {
        FeishuSyncTask task = requireTask(taskId);
        task.heartbeat(cursorJson, counts.scanned(), counts.created(), counts.updated(), counts.skipped(), leaseDeadline());
        tasks.save(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int complete(UUID taskId, Counts counts) {
        FeishuSyncTask task = requireTask(taskId);
        FeishuSource source = sources.findById(task.getSourceId())
                .orElseThrow(() -> new EntityNotFoundException("飞书同步来源不存在"));
        List<FeishuRemoteDocument> stale = remoteDocuments.findStale(source.getId(), taskId);
        for (FeishuRemoteDocument item : stale) {
            knowledgeBases.deleteDocument(source.getKnowledgeBaseId(), item.getDocumentId());
            remoteDocuments.delete(item);
        }
        task.succeed(stale.size());
        tasks.save(task);
        source.markCompleted("扫描 %d，新增 %d，更新 %d，删除 %d，跳过 %d".formatted(
                counts.scanned(), counts.created(), counts.updated(), stale.size(), counts.skipped()));
        sources.save(source);
        return stale.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID taskId, Throwable error) {
        FeishuSyncTask task = requireTask(taskId);
        String message = error.getMessage() == null ? "飞书同步失败" : error.getMessage();
        boolean retryable = !(error instanceof FeishuApiException apiError) || apiError.isRetryable();
        FeishuSource source = sources.findById(task.getSourceId()).orElse(null);
        if (retryable && task.getAttemptCount() < properties.maxAttempts()) {
            long delay = Math.min(300, 1L << Math.min(8, task.getAttemptCount()));
            task.retry(message, Instant.now().plus(delay, ChronoUnit.SECONDS));
        } else {
            task.fail(message);
            if (source != null) {
                source.markFailed(message);
                sources.save(source);
            }
        }
        tasks.save(task);
    }

    private String title(FeishuApiClient.RemoteItem item) {
        return item.title() == null || item.title().isBlank() ? "未命名飞书文档" : item.title();
    }

    private FeishuSyncTask requireTask(UUID taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new EntityNotFoundException("飞书同步任务不存在"));
    }

    private Instant leaseDeadline() {
        return Instant.now().plus(properties.leaseSeconds(), ChronoUnit.SECONDS);
    }

    public enum Change { CREATED, UPDATED }
    public record RemoteSnapshot(UUID documentId, String revision, Instant updatedAt, String contentHash) { }
    public record Counts(int scanned, int created, int updated, int skipped) { }
    public record SyncContext(FeishuSyncTask task, FeishuSource source, FeishuConnection connection) { }
}
