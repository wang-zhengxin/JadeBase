package ai.jadebase.knowledge.domain;

import ai.jadebase.knowledge.infra.ChunkRepository;
import ai.jadebase.knowledge.infra.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DocumentTaskStateService {

    private final DocumentRepository documents;
    private final ChunkRepository chunks;

    public DocumentTaskStateService(DocumentRepository documents, ChunkRepository chunks) {
        this.documents = documents;
        this.chunks = chunks;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Document begin(UUID documentId) {
        Document document = require(documentId);
        if (document.getStatus() != Document.Status.QUEUED) return null;
        document.markProcessing();
        chunks.deleteByDocumentId(documentId);
        return documents.save(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progress(UUID documentId, int progress) {
        Document document = require(documentId);
        document.updateProgress(progress);
        documents.save(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID documentId, int chunkCount) {
        Document document = require(documentId);
        document.markReady(chunkCount);
        documents.save(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID documentId, String message) {
        Document document = require(documentId);
        document.markFailed(message == null ? "索引任务执行失败" : message);
        documents.save(document);
    }

    private Document require(UUID documentId) {
        return documents.findById(documentId).orElseThrow(() -> new EntityNotFoundException("文档不存在"));
    }
}
