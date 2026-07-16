package ai.jadebase.connector.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class FeishuSyncExecutor {

    private final FeishuApiClient api;
    private final ConnectorSecretCipher cipher;
    private final FeishuSyncStateService state;
    private final ObjectMapper json;
    private final Timer duration;
    private final Counter successes;
    private final Counter failures;
    private final Counter createdDocuments;
    private final Counter updatedDocuments;
    private final Counter deletedDocuments;
    private final Counter skippedDocuments;

    public FeishuSyncExecutor(FeishuApiClient api, ConnectorSecretCipher cipher,
                              FeishuSyncStateService state, ObjectMapper json, MeterRegistry meters) {
        this.api = api;
        this.cipher = cipher;
        this.state = state;
        this.json = json;
        this.duration = meters.timer("jadebase.connector.sync.duration", "connector", "feishu");
        this.successes = meters.counter("jadebase.connector.sync.completed", "connector", "feishu", "result", "success");
        this.failures = meters.counter("jadebase.connector.sync.completed", "connector", "feishu", "result", "failure");
        this.createdDocuments = meters.counter("jadebase.connector.documents", "connector", "feishu", "result", "created");
        this.updatedDocuments = meters.counter("jadebase.connector.documents", "connector", "feishu", "result", "updated");
        this.deletedDocuments = meters.counter("jadebase.connector.documents", "connector", "feishu", "result", "deleted");
        this.skippedDocuments = meters.counter("jadebase.connector.documents", "connector", "feishu", "result", "skipped");
    }

    public void execute(UUID taskId) {
        Timer.Sample sample = Timer.start();
        try {
            FeishuSyncStateService.SyncContext context = state.context(taskId);
            FeishuSource source = context.source();
            FeishuConnection connection = context.connection();
            String token = api.authenticate(connection, cipher.decrypt(connection.getEncryptedAppSecret()));
            TraversalCursor cursor = cursor(context.task(), source);
            MutableCounts counts = new MutableCounts(context.task());

            while (cursor.current() != null || !cursor.pending().isEmpty()) {
                if (cursor.current() == null) {
                    cursor = new TraversalCursor(cursor.pending().subList(1, cursor.pending().size()),
                            cursor.pending().getFirst(), null);
                }
                FeishuApiClient.Page<FeishuApiClient.RemoteItem> page = source.getSourceType() == FeishuSource.Type.WIKI
                        ? api.listWikiNodes(connection, token, source.getRemoteId(), cursor.current(), cursor.pageToken())
                        : api.listFolderItems(connection, token, cursor.current(), cursor.pageToken());

                List<String> pending = new ArrayList<>(cursor.pending());
                for (FeishuApiClient.RemoteItem item : page.items()) {
                    if (item.container()) {
                        String child = source.getSourceType() == FeishuSource.Type.WIKI
                                ? item.nodeToken() : item.objectToken();
                        if (child != null && !child.isBlank()) pending.add(child);
                        if (source.getSourceType() == FeishuSource.Type.FOLDER) continue;
                    }
                    counts.scanned++;
                    if (!"docx".equalsIgnoreCase(item.objectType())) {
                        counts.skipped++;
                        continue;
                    }
                    try {
                        syncDocument(taskId, source, connection, token, item, counts);
                    } catch (FeishuApiException exception) {
                        if (!isDeleted(exception)) throw exception;
                        counts.skipped++;
                    }
                }

                String current = page.hasMore() && page.nextPageToken() != null
                        && !page.nextPageToken().isBlank() ? cursor.current() : null;
                String pageToken = current == null ? null : page.nextPageToken();
                cursor = new TraversalCursor(List.copyOf(pending), current, pageToken);
                state.heartbeat(taskId, json.writeValueAsString(cursor), counts.snapshot());
            }
            int deleted = state.complete(taskId, counts.snapshot());
            successes.increment();
            createdDocuments.increment(counts.created);
            updatedDocuments.increment(counts.updated);
            deletedDocuments.increment(deleted);
            skippedDocuments.increment(counts.skipped);
        } catch (Throwable error) {
            state.fail(taskId, error);
            failures.increment();
        } finally {
            sample.stop(duration);
        }
    }

    private void syncDocument(UUID taskId, FeishuSource source, FeishuConnection connection, String token,
                              FeishuApiClient.RemoteItem item, MutableCounts counts) {
        FeishuApiClient.DocxDocument document = api.getDocument(connection, token, item.objectToken());
        FeishuApiClient.RemoteItem normalized = new FeishuApiClient.RemoteItem(item.objectToken(), item.nodeToken(),
                item.parentToken(), item.objectType(), document.title().isBlank() ? item.title() : document.title(),
                item.author(), item.sourceUrl(), item.updatedAt(), false);
        var previous = state.remote(source.getId(), item.objectToken());
        if (previous.isPresent() && Objects.equals(previous.get().revision(), document.revision())) {
            state.markSeen(taskId, source, normalized);
            return;
        }
        String content = api.getRawContent(connection, token, item.objectToken());
        String contentHash = sha256(content);
        if (previous.isPresent() && Objects.equals(previous.get().contentHash(), contentHash)) {
            state.markUnchangedContent(taskId, source, normalized, document.revision(), contentHash);
            return;
        }
        FeishuSyncStateService.Change change = state.upsert(taskId, source, normalized,
                document.revision(), contentHash, content);
        if (change == FeishuSyncStateService.Change.CREATED) counts.created++;
        else counts.updated++;
    }

    private TraversalCursor cursor(FeishuSyncTask task, FeishuSource source) throws Exception {
        if (task.getCursorJson() != null && !task.getCursorJson().isBlank()) {
            return json.readValue(task.getCursorJson(), TraversalCursor.class);
        }
        String root = source.getSourceType() == FeishuSource.Type.WIKI ? "" : source.getRemoteId();
        return new TraversalCursor(List.of(), root, null);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算飞书文档摘要", exception);
        }
    }

    private boolean isDeleted(FeishuApiException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        return message.contains("not found") || message.contains("resource deleted")
                || message.contains("code=1770002") || message.contains("code=1770003");
    }

    public record TraversalCursor(List<String> pending, String current, String pageToken) {
        public TraversalCursor {
            pending = pending == null ? List.of() : List.copyOf(pending);
        }
    }

    private static final class MutableCounts {
        private int scanned;
        private int created;
        private int updated;
        private int skipped;

        private MutableCounts(FeishuSyncTask task) {
            this.scanned = task.getScannedCount();
            this.created = task.getCreatedCount();
            this.updated = task.getUpdatedCount();
            this.skipped = task.getSkippedCount();
        }

        private FeishuSyncStateService.Counts snapshot() {
            return new FeishuSyncStateService.Counts(scanned, created, updated, skipped);
        }
    }
}
