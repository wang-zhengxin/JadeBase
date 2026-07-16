package ai.jadebase.knowledge.domain;

import ai.jadebase.knowledge.infra.DocumentIndexTaskRepository;
import ai.jadebase.knowledge.infra.DocumentRepository;
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
public class DocumentIndexQueue {

    private final DocumentIndexTaskRepository tasks;
    private final DocumentRepository documents;
    private final IndexingProperties properties;
    private final DocumentProgressBroker progressBroker;

    public DocumentIndexQueue(DocumentIndexTaskRepository tasks, DocumentRepository documents,
                              IndexingProperties properties, DocumentProgressBroker progressBroker) {
        this.tasks = tasks;
        this.documents = documents;
        this.properties = properties;
        this.progressBroker = progressBroker;
    }

    @Transactional
    public DocumentIndexTask enqueue(UUID documentId) {
        return tasks.save(new DocumentIndexTask(documentId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedTask> claim(String workerId) {
        List<DocumentIndexTask> found = tasks.findClaimable(DocumentIndexTask.Status.QUEUED,
                Instant.now(), PageRequest.of(0, 1));
        if (found.isEmpty()) return Optional.empty();
        DocumentIndexTask task = found.getFirst();
        task.claim(workerId, leaseDeadline());
        tasks.save(task);
        return Optional.of(new ClaimedTask(task.getId(), task.getDocumentId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void heartbeat(UUID taskId) {
        tasks.findById(taskId).ifPresent(task -> {
            task.heartbeat(leaseDeadline());
            tasks.save(task);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(UUID taskId) {
        tasks.findById(taskId).ifPresent(task -> {
            task.succeed();
            tasks.save(task);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID taskId, String message) {
        tasks.findById(taskId).ifPresent(task -> {
            task.fail(message == null ? "索引任务执行失败" : message);
            tasks.save(task);
        });
    }

    @Transactional
    public int recoverExpired() {
        List<DocumentIndexTask> expired = tasks.findExpired(DocumentIndexTask.Status.RUNNING, Instant.now());
        for (DocumentIndexTask task : expired) {
            task.recover();
            documents.findById(task.getDocumentId()).ifPresent(document -> {
                document.recoverInterruptedTask();
                progressBroker.publish(documents.save(document));
            });
        }
        tasks.saveAll(expired);
        return expired.size();
    }

    private Instant leaseDeadline() {
        return Instant.now().plus(properties.leaseSeconds(), ChronoUnit.SECONDS);
    }

    public record ClaimedTask(UUID taskId, UUID documentId) { }
}
