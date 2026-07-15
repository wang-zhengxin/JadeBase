package ai.jadebase.knowledge.domain;

import ai.jadebase.knowledge.infra.ChunkRepository;
import ai.jadebase.knowledge.infra.DocumentRepository;
import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import ai.jadebase.rag.application.VectorCodec;
import ai.jadebase.rag.domain.EmbeddingClient;
import jakarta.persistence.EntityNotFoundException;
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
    private final ChunkRepository chunks;
    private final DocumentExtractor extractor;
    private final ChunkingService chunkingService;
    private final EmbeddingClient embeddingClient;
    private final VectorCodec vectorCodec;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBases, DocumentRepository documents,
                                ChunkRepository chunks, DocumentExtractor extractor,
                                ChunkingService chunkingService, EmbeddingClient embeddingClient,
                                VectorCodec vectorCodec) {
        this.knowledgeBases = knowledgeBases;
        this.documents = documents;
        this.chunks = chunks;
        this.extractor = extractor;
        this.chunkingService = chunkingService;
        this.embeddingClient = embeddingClient;
        this.vectorCodec = vectorCodec;
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
        try {
            String content = extractor.extract(file);
            List<String> parts = chunkingService.split(content);
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                chunks.save(new Chunk(knowledgeBaseId, document.getId(), name, i, part,
                        vectorCodec.encode(embeddingClient.embed(part))));
            }
            document.markReady(parts.size());
        } catch (RuntimeException | IOException exception) {
            document.markFailed(exception.getMessage());
            documents.save(document);
            throw exception;
        }
        return documents.save(document);
    }

    @Transactional
    public void deleteDocument(UUID knowledgeBaseId, UUID documentId) {
        Document document = documents.findById(documentId)
                .filter(value -> value.getKnowledgeBaseId().equals(knowledgeBaseId))
                .orElseThrow(() -> new EntityNotFoundException("文档不存在"));
        chunks.deleteByDocumentId(documentId);
        documents.delete(document);
    }

    private KnowledgeBase requireKnowledgeBase(UUID id) {
        return knowledgeBases.findById(id).orElseThrow(() -> new EntityNotFoundException("知识库不存在"));
    }
}
