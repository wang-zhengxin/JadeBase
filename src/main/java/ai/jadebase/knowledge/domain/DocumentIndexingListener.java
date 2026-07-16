package ai.jadebase.knowledge.domain;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentIndexingListener {

    private final DocumentIndexingService indexingService;

    public DocumentIndexingListener(DocumentIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @Async("documentIndexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIndexRequested(DocumentIndexRequested event) {
        indexingService.index(event.documentId());
    }
}
