package ai.jadebase.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "message_sources", indexes = @Index(name = "idx_source_message", columnList = "message_id,source_index"))
public class MessageSource {

    @Id
    @GeneratedValue
    private UUID id;
    @Column(name = "message_id")
    private UUID messageId;
    @Column(name = "source_index")
    private int sourceIndex;
    private UUID documentId;
    private String documentName;
    private int chunkIndex;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String snippet;
    private double score;

    protected MessageSource() {
    }

    public MessageSource(UUID messageId, int sourceIndex, UUID documentId, String documentName,
                         int chunkIndex, String snippet, double score) {
        this.messageId = messageId;
        this.sourceIndex = sourceIndex;
        this.documentId = documentId;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.snippet = snippet;
        this.score = score;
    }

    public UUID getId() { return id; }
    public UUID getMessageId() { return messageId; }
    public int getSourceIndex() { return sourceIndex; }
    public UUID getDocumentId() { return documentId; }
    public String getDocumentName() { return documentName; }
    public int getChunkIndex() { return chunkIndex; }
    public String getSnippet() { return snippet; }
    public double getScore() { return score; }
}
