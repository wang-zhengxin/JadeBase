package ai.jadebase.knowledge.domain;

import ai.jadebase.knowledge.infra.ChunkRepository;
import ai.jadebase.knowledge.infra.ChunkTermRepository;
import ai.jadebase.knowledge.infra.DocumentIndexTaskRepository;
import ai.jadebase.knowledge.infra.DocumentRepository;
import ai.jadebase.knowledge.infra.DocumentPayloadRepository;
import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBases;
    private final DocumentRepository documents;
    private final DocumentPayloadRepository payloads;
    private final ChunkRepository chunks;
    private final ChunkTermRepository chunkTerms;
    private final DocumentIndexTaskRepository tasks;
    private final DocumentIndexQueue indexQueue;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBases, DocumentRepository documents,
                                DocumentPayloadRepository payloads, ChunkRepository chunks,
                                ChunkTermRepository chunkTerms, DocumentIndexTaskRepository tasks,
                                DocumentIndexQueue indexQueue) {
        this.knowledgeBases = knowledgeBases;
        this.documents = documents;
        this.payloads = payloads;
        this.chunks = chunks;
        this.chunkTerms = chunkTerms;
        this.tasks = tasks;
        this.indexQueue = indexQueue;
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
        indexQueue.enqueue(document.getId());
        return document;
    }

    @Transactional
    public Document upsertRemoteDocument(UUID knowledgeBaseId, UUID existingDocumentId, RemoteDocument content) {
        requireKnowledgeBase(knowledgeBaseId);
        byte[] bytes = content.content().getBytes(StandardCharsets.UTF_8);
        String filename = markdownFilename(content.name());
        if (existingDocumentId == null) {
            Document document = new Document(knowledgeBaseId, content.name(), "text/markdown", bytes.length);
            document.applyRemoteMetadata(content.name(), content.externalId(), content.sourceUrl(),
                    content.author(), content.updatedAt());
            document = documents.save(document);
            payloads.save(new DocumentPayload(document.getId(), filename, "text/markdown", bytes));
            indexQueue.enqueue(document.getId());
            return document;
        }

        Document document = documents.findById(existingDocumentId)
                .filter(value -> value.getKnowledgeBaseId().equals(knowledgeBaseId))
                .orElseThrow(() -> new EntityNotFoundException("远程文档不存在"));
        payloads.save(new DocumentPayload(document.getId(), filename, "text/markdown", bytes));
        boolean enqueue = document.applyRemoteContent(content.name(), "text/markdown", bytes.length,
                content.externalId(), content.sourceUrl(), content.author(), content.updatedAt());
        Document saved = documents.save(document);
        if (enqueue) indexQueue.enqueue(document.getId());
        return saved;
    }

    @Transactional
    public Document updateRemoteMetadata(UUID knowledgeBaseId, UUID documentId, RemoteDocumentMetadata metadata) {
        Document document = documents.findById(documentId)
                .filter(value -> value.getKnowledgeBaseId().equals(knowledgeBaseId))
                .orElseThrow(() -> new EntityNotFoundException("远程文档不存在"));
        document.applyRemoteMetadata(metadata.name(), metadata.externalId(), metadata.sourceUrl(),
                metadata.author(), metadata.updatedAt());
        return documents.save(document);
    }

    @Transactional
    public Document retryDocument(UUID knowledgeBaseId, UUID documentId) {
        Document document = documents.findById(documentId)
                .filter(value -> value.getKnowledgeBaseId().equals(knowledgeBaseId))
                .orElseThrow(() -> new EntityNotFoundException("文档不存在"));
        document.queueForRetry();
        documents.save(document);
        indexQueue.enqueue(documentId);
        return document;
    }

    @Transactional
    public Document reindexDocument(UUID knowledgeBaseId, UUID documentId) {
        Document document = documents.findById(documentId)
                .filter(value -> value.getKnowledgeBaseId().equals(knowledgeBaseId))
                .orElseThrow(() -> new EntityNotFoundException("文档不存在"));
        if (!payloads.existsById(documentId)) throw new IllegalStateException("文档原始内容不存在，无法重新索引");
        document.queueForReindex();
        documents.save(document);
        indexQueue.enqueue(documentId);
        return document;
    }

    @Transactional
    public List<Document> reindexKnowledgeBase(UUID knowledgeBaseId) {
        requireKnowledgeBase(knowledgeBaseId);
        return documents.findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId).stream()
                .filter(document -> document.getStatus() != Document.Status.QUEUED
                        && document.getStatus() != Document.Status.PROCESSING)
                .filter(document -> payloads.existsById(document.getId()))
                .map(document -> {
                    document.queueForReindex();
                    documents.save(document);
                    indexQueue.enqueue(document.getId());
                    return document;
                })
                .toList();
    }

    @Transactional
    public void deleteDocument(UUID knowledgeBaseId, UUID documentId) {
        Document document = documents.findById(documentId)
                .filter(value -> value.getKnowledgeBaseId().equals(knowledgeBaseId))
                .orElseThrow(() -> new EntityNotFoundException("文档不存在"));
        chunkTerms.deleteByDocumentId(documentId);
        chunks.deleteByDocumentId(documentId);
        tasks.deleteByDocumentId(documentId);
        if (payloads.existsById(documentId)) {
            payloads.deleteById(documentId);
        }
        documents.delete(document);
    }

    private KnowledgeBase requireKnowledgeBase(UUID id) {
        return knowledgeBases.findById(id).orElseThrow(() -> new EntityNotFoundException("知识库不存在"));
    }

    private String markdownFilename(String name) {
        String normalized = name == null || name.isBlank() ? "飞书文档" : name.trim();
        return normalized.toLowerCase(java.util.Locale.ROOT).endsWith(".md") ? normalized : normalized + ".md";
    }

    public record RemoteDocument(String name, String externalId, String sourceUrl,
                                 String author, Instant updatedAt, String content) { }
    public record RemoteDocumentMetadata(String name, String externalId, String sourceUrl,
                                         String author, Instant updatedAt) { }
}
