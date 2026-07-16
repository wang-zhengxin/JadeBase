package ai.jadebase.connector.feishu;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FeishuSyncQueue {

    private static final List<FeishuSyncTask.Status> ACTIVE = List.of(
            FeishuSyncTask.Status.QUEUED, FeishuSyncTask.Status.RUNNING);
    private final FeishuSyncTaskRepository tasks;
    private final FeishuSourceRepository sources;
    private final FeishuConnectorProperties properties;

    public FeishuSyncQueue(FeishuSyncTaskRepository tasks, FeishuSourceRepository sources,
                           FeishuConnectorProperties properties) {
        this.tasks = tasks;
        this.sources = sources;
        this.properties = properties;
    }

    @Transactional
    public FeishuSyncTask enqueue(UUID sourceId, FeishuSyncTask.Mode mode) {
        FeishuSource source = sources.findById(sourceId)
                .orElseThrow(() -> new EntityNotFoundException("飞书同步来源不存在"));
        if (!source.isEnabled()) throw new IllegalStateException("飞书同步来源已停用");
        Optional<FeishuSyncTask> active = tasks.findTopBySourceIdOrderByCreatedAtDesc(sourceId)
                .filter(task -> ACTIVE.contains(task.getStatus()));
        return active.orElseGet(() -> tasks.save(new FeishuSyncTask(sourceId, mode)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedTask> claim(String workerId) {
        List<FeishuSyncTask> found = tasks.findClaimable(FeishuSyncTask.Status.QUEUED,
                Instant.now(), PageRequest.of(0, 1));
        if (found.isEmpty()) return Optional.empty();
        FeishuSyncTask task = found.getFirst();
        task.claim(workerId, leaseDeadline());
        tasks.save(task);
        sources.findById(task.getSourceId()).ifPresent(source -> {
            source.markStarted();
            sources.save(source);
        });
        return Optional.of(new ClaimedTask(task.getId()));
    }

    @Transactional
    public FeishuSyncTask retry(UUID taskId) {
        FeishuSyncTask task = tasks.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("飞书同步任务不存在"));
        if (task.getStatus() != FeishuSyncTask.Status.FAILED) {
            throw new IllegalStateException("只有失败的同步任务可以重试");
        }
        if (tasks.existsBySourceIdAndStatusIn(task.getSourceId(), ACTIVE)) {
            throw new IllegalStateException("该来源已有同步任务运行中");
        }
        task.manualRetry();
        return tasks.save(task);
    }

    @Transactional
    public int recoverExpired() {
        List<FeishuSyncTask> expired = tasks.findExpired(FeishuSyncTask.Status.RUNNING, Instant.now());
        expired.forEach(FeishuSyncTask::recover);
        tasks.saveAll(expired);
        return expired.size();
    }

    @Transactional
    public int scheduleDue() {
        int enqueued = 0;
        Instant now = Instant.now();
        for (FeishuSource source : sources.findByEnabledTrue()) {
            Optional<FeishuSyncTask> latest = tasks.findTopBySourceIdOrderByCreatedAtDesc(source.getId());
            if (latest.filter(task -> task.getStatus() == FeishuSyncTask.Status.FAILED).isPresent()) continue;
            Instant last = source.getLastSyncCompletedAt();
            if (last != null && last.plus(source.getSyncIntervalMinutes(), ChronoUnit.MINUTES).isAfter(now)) continue;
            if (!tasks.existsBySourceIdAndStatusIn(source.getId(), ACTIVE)) {
                tasks.save(new FeishuSyncTask(source.getId(), last == null
                        ? FeishuSyncTask.Mode.FULL : FeishuSyncTask.Mode.INCREMENTAL));
                enqueued++;
            }
        }
        return enqueued;
    }

    private Instant leaseDeadline() {
        return Instant.now().plus(properties.leaseSeconds(), ChronoUnit.SECONDS);
    }

    public record ClaimedTask(UUID taskId) { }
}
