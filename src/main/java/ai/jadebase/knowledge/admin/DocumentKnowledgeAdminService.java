package ai.jadebase.knowledge.admin;

import ai.jadebase.knowledge.domain.Document;
import ai.jadebase.knowledge.domain.IndexSettingsService;
import ai.jadebase.knowledge.domain.KnowledgeBase;
import ai.jadebase.knowledge.domain.KnowledgeBaseService;
import ai.jadebase.knowledge.infra.DocumentRepository;
import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import ai.jadebase.workspace.domain.WorkspaceSettings;
import ai.jadebase.workspace.domain.WorkspaceSettingsService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentKnowledgeAdminService {

    private final DocumentSetRepository documentSets;
    private final DocumentSetItemRepository setItems;
    private final DocumentRepository documents;
    private final KnowledgeBaseRepository knowledgeBases;
    private final KnowledgeBaseService knowledgeBaseService;
    private final IndexSettingsService indexSettings;
    private final WorkspaceSettingsService workspaceSettings;

    public DocumentKnowledgeAdminService(DocumentSetRepository documentSets, DocumentSetItemRepository setItems,
                                         DocumentRepository documents, KnowledgeBaseRepository knowledgeBases,
                                         KnowledgeBaseService knowledgeBaseService, IndexSettingsService indexSettings,
                                         WorkspaceSettingsService workspaceSettings) {
        this.documentSets = documentSets;
        this.setItems = setItems;
        this.documents = documents;
        this.knowledgeBases = knowledgeBases;
        this.knowledgeBaseService = knowledgeBaseService;
        this.indexSettings = indexSettings;
        this.workspaceSettings = workspaceSettings;
    }

    @Transactional(readOnly = true)
    public InventorySummary summary() {
        List<Document> all = documents.findAll();
        return new InventorySummary(knowledgeBases.count(), all.size(), documentSets.count(),
                all.stream().filter(item -> item.getStatus() == Document.Status.READY).count(),
                all.stream().filter(item -> item.getStatus() == Document.Status.QUEUED
                        || item.getStatus() == Document.Status.PROCESSING).count(),
                all.stream().filter(item -> item.getStatus() == Document.Status.FAILED).count(),
                all.stream().mapToLong(Document::getChunkCount).sum(),
                all.stream().mapToLong(Document::getSizeBytes).sum());
    }

    @Transactional(readOnly = true)
    public List<DocumentView> inventory() {
        Map<UUID, String> names = knowledgeBases.findAll().stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));
        return documents.findAll().stream()
                .sorted(Comparator.comparing(Document::getCreatedAt).reversed())
                .map(item -> documentView(item, names.getOrDefault(item.getKnowledgeBaseId(), "已删除知识库")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentSetView> listDocumentSets() {
        Map<UUID, DocumentView> inventory = inventory().stream()
                .collect(Collectors.toMap(DocumentView::id, Function.identity()));
        return documentSets.findAllByOrderByUpdatedAtDesc().stream().map(set -> view(set, inventory)).toList();
    }

    @Transactional
    public DocumentSetView createDocumentSet(DocumentSetInput input) {
        DocumentSet set = documentSets.save(new DocumentSet(name(input.name()), description(input.description())));
        syncItems(set.getId(), documentIds(input.documentIds()));
        return view(set, inventoryMap());
    }

    @Transactional
    public DocumentSetView updateDocumentSet(UUID setId, DocumentSetInput input) {
        DocumentSet set = requireSet(setId);
        set.update(name(input.name()), description(input.description()));
        documentSets.save(set);
        syncItems(setId, documentIds(input.documentIds()));
        return view(set, inventoryMap());
    }

    @Transactional
    public void deleteDocumentSet(UUID setId) {
        DocumentSet set = requireSet(setId);
        setItems.deleteByDocumentSetId(setId);
        documentSets.delete(set);
    }

    @Transactional
    public IndexSettingsView indexSettings() {
        return settingsView(indexSettings.get(), workspaceSettings.get().getTopK());
    }

    @Transactional
    public IndexSettingsView updateIndexSettings(IndexSettingsInput input) {
        WorkspaceSettings workspace = workspaceSettings.updateTopK(input.topK());
        IndexSettings saved = indexSettings.update(input.chunkSize(), input.chunkOverlap(), input.candidateK(),
                input.rrfK(), input.rerankEnabled(), input.queryRewriteEnabled());
        return settingsView(saved, workspace.getTopK());
    }

    public ReindexResult reindexAll() {
        int queued = knowledgeBases.findAll().stream()
                .mapToInt(knowledgeBase -> knowledgeBaseService.reindexKnowledgeBase(knowledgeBase.getId()).size())
                .sum();
        IndexSettings settings = indexSettings.markReindexed();
        return new ReindexResult(queued, settings.isReindexRequired(), "已将 " + queued + " 个文档加入重建队列");
    }

    private void syncItems(UUID setId, List<UUID> ids) {
        List<Document> found = documents.findAllById(ids);
        if (found.size() != ids.size()) throw new EntityNotFoundException("所选文档不存在或已删除");
        setItems.deleteByDocumentSetId(setId);
        ids.forEach(id -> setItems.save(new DocumentSetItem(setId, id)));
    }

    private DocumentSetView view(DocumentSet set, Map<UUID, DocumentView> inventory) {
        List<DocumentView> members = setItems.findByDocumentSetIdOrderByCreatedAtAsc(set.getId()).stream()
                .map(DocumentSetItem::getDocumentId).map(inventory::get).filter(java.util.Objects::nonNull).toList();
        long ready = members.stream().filter(item -> item.status() == Document.Status.READY).count();
        return new DocumentSetView(set.getId(), set.getName(), set.getDescription(), members.size(), ready,
                members.stream().mapToLong(DocumentView::chunkCount).sum(), set.getCreatedAt(), set.getUpdatedAt(), members);
    }

    private Map<UUID, DocumentView> inventoryMap() {
        return inventory().stream().collect(Collectors.toMap(DocumentView::id, Function.identity()));
    }

    private DocumentView documentView(Document item, String knowledgeBaseName) {
        return new DocumentView(item.getId(), item.getKnowledgeBaseId(), knowledgeBaseName, item.getName(),
                item.getContentType(), item.getSizeBytes(), item.getChunkCount(), item.getStatus(), item.getProgress(),
                item.getSourceType() == null ? "UPLOAD" : item.getSourceType(), item.getCreatedAt());
    }

    private IndexSettingsView settingsView(IndexSettings settings, int topK) {
        return new IndexSettingsView(settings.getChunkSize(), settings.getChunkOverlap(), topK,
                settings.getCandidateK(), settings.getRrfK(), settings.isRerankEnabled(),
                settings.isQueryRewriteEnabled(), settings.isReindexRequired(), settings.getUpdatedAt());
    }

    private DocumentSet requireSet(UUID id) {
        return documentSets.findById(id).orElseThrow(() -> new EntityNotFoundException("文档集不存在"));
    }

    private String name(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("文档集名称不能为空");
        String result = value.trim();
        if (result.length() > 120) throw new IllegalArgumentException("文档集名称不能超过 120 个字符");
        return result;
    }

    private String description(String value) {
        String result = value == null ? "" : value.trim();
        if (result.length() > 500) throw new IllegalArgumentException("文档集描述不能超过 500 个字符");
        return result;
    }

    private List<UUID> documentIds(List<UUID> values) {
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        if (values != null) values.stream().filter(java.util.Objects::nonNull).forEach(unique::add);
        if (unique.size() > 500) throw new IllegalArgumentException("单个文档集最多包含 500 个文档");
        return List.copyOf(unique);
    }

    public record InventorySummary(long knowledgeBaseCount, long documentCount, long documentSetCount,
                                   long readyCount, long indexingCount, long failedCount,
                                   long chunkCount, long sizeBytes) { }
    public record DocumentView(UUID id, UUID knowledgeBaseId, String knowledgeBaseName, String name,
                               String contentType, long sizeBytes, long chunkCount, Document.Status status,
                               int progress, String sourceType, Instant createdAt) { }
    public record DocumentSetInput(String name, String description, List<UUID> documentIds) { }
    public record DocumentSetView(UUID id, String name, String description, int documentCount, long readyCount,
                                  long chunkCount, Instant createdAt, Instant updatedAt,
                                  List<DocumentView> documents) { }
    public record IndexSettingsInput(int chunkSize, int chunkOverlap, int topK, int candidateK, int rrfK,
                                     boolean rerankEnabled, boolean queryRewriteEnabled) { }
    public record IndexSettingsView(int chunkSize, int chunkOverlap, int topK, int candidateK, int rrfK,
                                    boolean rerankEnabled, boolean queryRewriteEnabled,
                                    boolean reindexRequired, Instant updatedAt) { }
    public record ReindexResult(int queuedDocuments, boolean reindexRequired, String message) { }
}
