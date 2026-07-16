package ai.jadebase.knowledge.domain;

import ai.jadebase.knowledge.infra.DocumentPayloadRepository;
import ai.jadebase.rag.application.VectorCodec;
import ai.jadebase.rag.domain.EmbeddingClient;
import jakarta.persistence.EntityNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIndexingService {

    private final DocumentPayloadRepository payloads;
    private final DocumentExtractor extractor;
    private final ChunkingService chunkingService;
    private final EmbeddingClient embeddingClient;
    private final VectorCodec vectorCodec;
    private final DocumentTaskStateService taskState;
    private final DocumentIndexQueue queue;
    private final Timer indexingTimer;
    private final Counter indexingSuccess;
    private final Counter indexingFailure;

    public DocumentIndexingService(DocumentPayloadRepository payloads,
                                   DocumentExtractor extractor, ChunkingService chunkingService,
                                   EmbeddingClient embeddingClient, VectorCodec vectorCodec,
                                   DocumentTaskStateService taskState, DocumentIndexQueue queue,
                                   MeterRegistry meters) {
        this.payloads = payloads;
        this.extractor = extractor;
        this.chunkingService = chunkingService;
        this.embeddingClient = embeddingClient;
        this.vectorCodec = vectorCodec;
        this.taskState = taskState;
        this.queue = queue;
        this.indexingTimer = meters.timer("jadebase.indexing.duration");
        this.indexingSuccess = meters.counter("jadebase.indexing.completed", "result", "success");
        this.indexingFailure = meters.counter("jadebase.indexing.completed", "result", "failure");
    }

    public void index(UUID taskId, UUID documentId) {
        Timer.Sample sample = Timer.start();
        try {
            Document document = taskState.begin(documentId);
            if (document == null) {
                queue.succeed(taskId);
                return;
            }
            DocumentPayload payload = payloads.findById(documentId)
                    .orElseThrow(() -> new EntityNotFoundException("文档原始内容不存在"));
            String content = extractor.extract(payload.getFilename(), payload.getContent());
            taskState.progress(documentId, 30);
            List<String> parts = chunkingService.split(content);
            List<Chunk> replacements = new java.util.ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                replacements.add(new Chunk(document.getKnowledgeBaseId(), documentId, document.getName(), i, part,
                        vectorCodec.encode(embeddingClient.embed(part))));
                taskState.progress(documentId, 30 + (int) Math.round(65.0 * (i + 1) / parts.size()));
                queue.heartbeat(taskId);
            }
            taskState.replaceChunksAndComplete(documentId, replacements);
            queue.succeed(taskId);
            indexingSuccess.increment();
        } catch (RuntimeException | IOException exception) {
            try {
                taskState.fail(documentId, exception.getMessage());
            } catch (EntityNotFoundException ignored) {
                // The document may have been intentionally deleted while the worker was running.
            }
            queue.fail(taskId, exception.getMessage());
            indexingFailure.increment();
        } finally {
            sample.stop(indexingTimer);
        }
    }
}
