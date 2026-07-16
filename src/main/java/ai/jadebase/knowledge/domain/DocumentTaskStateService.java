package ai.jadebase.knowledge.domain;

import ai.jadebase.knowledge.infra.ChunkRepository;
import ai.jadebase.knowledge.infra.ChunkTermRepository;
import ai.jadebase.knowledge.infra.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
public class DocumentTaskStateService {

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final ChunkTermRepository chunkTerms;
    private final ChunkTermIndexService termIndex;
    private final DocumentProgressBroker progressBroker;

    public DocumentTaskStateService(DocumentRepository documents, ChunkRepository chunks,
                                    ChunkTermRepository chunkTerms, ChunkTermIndexService termIndex,
                                    DocumentProgressBroker progressBroker) {
        this.documents = documents;
        this.chunks = chunks;
        this.chunkTerms = chunkTerms;
        this.termIndex = termIndex;
        this.progressBroker = progressBroker;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Document begin(UUID documentId) {
        Document document = require(documentId);
        if (document.getStatus() != Document.Status.QUEUED) return null;
        document.markProcessing();
        Document saved = documents.save(document);
        progressBroker.publish(saved);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progress(UUID documentId, int progress) {
        Document document = require(documentId);
        document.updateProgress(progress);
        progressBroker.publish(documents.save(document));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replaceChunksAndComplete(UUID documentId, List<Chunk> replacements) {
        Document document = require(documentId);
        chunkTerms.deleteByDocumentId(documentId);
        chunks.deleteByDocumentId(documentId);
        List<Chunk> savedChunks = chunks.saveAllAndFlush(replacements);
        savedChunks.forEach(chunk -> chunkTerms.saveAll(termIndex.termsFor(chunk)));
        document.markReady(savedChunks.size());
        progressBroker.publish(documents.save(document));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID documentId, String message) {
        Document document = require(documentId);
        document.markFailed(message == null ? "索引任务执行失败" : message);
        progressBroker.publish(documents.save(document));
    }

    private Document require(UUID documentId) {
        return documents.findById(documentId).orElseThrow(() -> new EntityNotFoundException("文档不存在"));
    }
}
