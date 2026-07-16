package ai.jadebase.connector.feishu;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executor;

@Component
public class FeishuSyncWorker {

    private final FeishuSyncQueue queue;
    private final FeishuSyncExecutor sync;
    private final Executor executor;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public FeishuSyncWorker(FeishuSyncQueue queue, FeishuSyncExecutor sync,
                            @Qualifier("connectorSyncExecutor") Executor executor) {
        this.queue = queue;
        this.sync = sync;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${jadebase.connectors.feishu.poll-delay-ms:1000}")
    public void poll() {
        queue.claim(workerId).ifPresent(task -> executor.execute(() -> sync.execute(task.taskId())));
    }

    @Scheduled(fixedDelayString = "${jadebase.connectors.feishu.recovery-delay-ms:30000}")
    public void recover() {
        queue.recoverExpired();
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 15000)
    public void schedule() {
        queue.scheduleDue();
    }
}
