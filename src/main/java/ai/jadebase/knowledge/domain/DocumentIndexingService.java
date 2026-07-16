package ai.jadebase.knowledge.domain;

import ai.jadebase.knowledge.infra.ChunkRepository;
import ai.jadebase.knowledge.infra.DocumentPayloadRepository;
import ai.jadebase.rag.application.VectorCodec;
import ai.jadebase.rag.domain.EmbeddingClient;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIndexingService {

    private final DocumentPayloadRepository payloads;
    private final ChunkRepository chunks;
    private final DocumentExtractor extractor;
    private final ChunkingService chunkingService;
    private final EmbeddingClient embeddingClient;
    private final VectorCodec vectorCodec;
    private final DocumentTaskStateService taskState;

    public DocumentIndexingService(DocumentPayloadRepository payloads, ChunkRepository chunks,
                                   DocumentExtractor extractor, ChunkingService chunkingService,
                                   EmbeddingClient embeddingClient, VectorCodec vectorCodec,
                                   DocumentTaskStateService taskState) {
        this.payloads = payloads;
        this.chunks = chunks;
        this.extractor = extractor;
        this.chunkingService = chunkingService;
        this.embeddingClient = embeddingClient;
        this.vectorCodec = vectorCodec;
        this.taskState = taskState;
    }

    public void index(UUID documentId) {
        Document document = taskState.begin(documentId);
        if (document == null) return;
        try {
            DocumentPayload payload = payloads.findById(documentId)
                    .orElseThrow(() -> new EntityNotFoundException("文档原始内容不存在"));
            String content = extractor.extract(payload.getFilename(), payload.getContent());
            taskState.progress(documentId, 30);
            List<String> parts = chunkingService.split(content);
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                chunks.save(new Chunk(document.getKnowledgeBaseId(), documentId, document.getName(), i, part,
                        vectorCodec.encode(embeddingClient.embed(part))));
                taskState.progress(documentId, 30 + (int) Math.round(65.0 * (i + 1) / parts.size()));
            }
            taskState.complete(documentId, parts.size());
            payloads.deleteById(documentId);
        } catch (RuntimeException | IOException exception) {
            taskState.fail(documentId, exception.getMessage());
        }
    }
}
