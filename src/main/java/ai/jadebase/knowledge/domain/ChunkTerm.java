package ai.jadebase.knowledge.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "document_chunk_terms", indexes = {
        @Index(name = "idx_chunk_term_lookup", columnList = "knowledge_base_id,term"),
        @Index(name = "idx_chunk_term_document", columnList = "document_id")
})
public class ChunkTerm {

    @Id
    @GeneratedValue
    private UUID id;
    private UUID chunkId;
    private UUID documentId;
    private UUID knowledgeBaseId;
    private String term;
    private int termFrequency;
    private int documentLength;

    protected ChunkTerm() {
    }

    public ChunkTerm(UUID chunkId, UUID documentId, UUID knowledgeBaseId, String term,
                     int termFrequency, int documentLength) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.term = term;
        this.termFrequency = termFrequency;
        this.documentLength = documentLength;
    }

    public UUID getId() { return id; }
    public UUID getChunkId() { return chunkId; }
}
