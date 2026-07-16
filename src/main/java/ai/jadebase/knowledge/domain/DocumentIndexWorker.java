package ai.jadebase.knowledge.domain;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executor;

@Component
public class DocumentIndexWorker {

    private final DocumentIndexQueue queue;
    private final DocumentIndexingService indexing;
    private final Executor executor;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public DocumentIndexWorker(DocumentIndexQueue queue, DocumentIndexingService indexing,
                               @Qualifier("documentIndexExecutor") Executor executor) {
        this.queue = queue;
        this.indexing = indexing;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${jadebase.indexing.poll-delay-ms:500}")
    public void poll() {
        queue.claim(workerId).ifPresent(task -> executor.execute(
                () -> indexing.index(task.taskId(), task.documentId())));
    }

    @Scheduled(fixedDelayString = "${jadebase.indexing.recovery-delay-ms:10000}")
    public void recover() {
        queue.recoverExpired();
    }
}
