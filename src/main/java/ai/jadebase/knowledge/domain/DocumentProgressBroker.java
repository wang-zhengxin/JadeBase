package ai.jadebase.knowledge.domain;

import org.springframework.stereotype.Component;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DocumentProgressBroker implements SmartLifecycle {

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public SseEmitter subscribe(UUID knowledgeBaseId, List<Document> snapshot) {
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        emitters.computeIfAbsent(knowledgeBaseId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(knowledgeBaseId, emitter));
        emitter.onTimeout(() -> remove(knowledgeBaseId, emitter));
        emitter.onError(error -> remove(knowledgeBaseId, emitter));
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (IOException exception) {
            remove(knowledgeBaseId, emitter);
        }
        return emitter;
    }

    public void publish(Document document) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emit(document);
                }
            });
            return;
        }
        emit(document);
    }

    private void emit(Document document) {
        List<SseEmitter> listeners = emitters.getOrDefault(document.getKnowledgeBaseId(), new CopyOnWriteArrayList<>());
        for (SseEmitter emitter : listeners) {
            try {
                emitter.send(SseEmitter.event().name("document").data(document));
            } catch (IOException exception) {
                remove(document.getKnowledgeBaseId(), emitter);
            }
        }
    }

    private void remove(UUID knowledgeBaseId, SseEmitter emitter) {
        List<SseEmitter> listeners = emitters.get(knowledgeBaseId);
        if (listeners != null) listeners.remove(emitter);
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        emitters.values().forEach(listeners -> listeners.forEach(SseEmitter::complete));
        emitters.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
