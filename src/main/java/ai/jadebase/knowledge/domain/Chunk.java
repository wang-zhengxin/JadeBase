package ai.jadebase.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class Chunk {

    @Id
    @GeneratedValue
    private UUID id;
    private UUID knowledgeBaseId;
    private UUID documentId;
    private String documentName;
    private int chunkIndex;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(columnDefinition = "TEXT")
    private String embedding;

    protected Chunk() {
    }

    public Chunk(UUID knowledgeBaseId, UUID documentId, String documentName,
                 int chunkIndex, String content, String embedding) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.documentId = documentId;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
    }

    public UUID getId() { return id; }
    public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
    public UUID getDocumentId() { return documentId; }
    public String getDocumentName() { return documentName; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public String getEmbedding() { return embedding; }
}
