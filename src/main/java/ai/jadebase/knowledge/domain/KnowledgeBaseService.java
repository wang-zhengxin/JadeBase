package ai.jadebase.knowledge.domain;

import ai.jadebase.knowledge.infra.ChunkRepository;
import ai.jadebase.knowledge.infra.DocumentRepository;
import ai.jadebase.knowledge.infra.DocumentPayloadRepository;
import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBases;
    private final DocumentRepository documents;
    private final DocumentPayloadRepository payloads;
    private final ChunkRepository chunks;
    private final ApplicationEventPublisher events;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBases, DocumentRepository documents,
                                DocumentPayloadRepository payloads, ChunkRepository chunks,
                                ApplicationEventPublisher events) {
        this.knowledgeBases = knowledgeBases;
        this.documents = documents;
        this.payloads = payloads;
        this.chunks = chunks;
        this.events = events;
    }

    @Transactional
    public KnowledgeBase create(String name, String description) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("知识库名称不能为空");
        return knowledgeBases.save(new KnowledgeBase(name.trim(), description == null ? "" : description.trim()));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBase> list() {
        return knowledgeBases.findAll();
    }

    @Transactional(readOnly = true)
    public List<Document> listDocuments(UUID knowledgeBaseId) {
        requireKnowledgeBase(knowledgeBaseId);
        return documents.findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId);
    }

    @Transactional
    public Document upload(UUID knowledgeBaseId, MultipartFile file) throws IOException {
        requireKnowledgeBase(knowledgeBaseId);
        if (file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        Document document = documents.save(new Document(knowledgeBaseId, name, file.getContentType(), file.getSize()));
        payloads.save(new DocumentPayload(document.getId(), name, file.getContentType(), file.getBytes()));
        events.publishEvent(new DocumentIndexRequested(document.getId()));
        return document;
    }

    @Transactional
    public Document retryDocument(UUID knowledgeBaseId, UUID documentId) {
        Document document = documents.findById(documentId)
                .filter(value -> value.getKnowledgeBaseId().equals(knowledgeBaseId))
                .orElseThrow(() -> new EntityNotFoundException("文档不存在"));
        document.queueForRetry();
        documents.save(document);
        events.publishEvent(new DocumentIndexRequested(documentId));
        return document;
    }

    @Transactional
    public void deleteDocument(UUID knowledgeBaseId, UUID documentId) {
        Document document = documents.findById(documentId)
                .filter(value -> value.getKnowledgeBaseId().equals(knowledgeBaseId))
                .orElseThrow(() -> new EntityNotFoundException("文档不存在"));
        chunks.deleteByDocumentId(documentId);
        if (payloads.existsById(documentId)) {
            payloads.deleteById(documentId);
        }
        documents.delete(document);
    }

    private KnowledgeBase requireKnowledgeBase(UUID id) {
        return knowledgeBases.findById(id).orElseThrow(() -> new EntityNotFoundException("知识库不存在"));
    }
}
