package ai.jadebase.knowledge.domain;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class LegacyChunkTermBackfill {

    private final ChunkTermIndexService termIndex;

    public LegacyChunkTermBackfill(ChunkTermIndexService termIndex) {
        this.termIndex = termIndex;
    }

    @Async("documentIndexExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        while (termIndex.backfillBatch(200) == 200) {
            // Continue until the final partial batch has been indexed.
        }
    }
}
