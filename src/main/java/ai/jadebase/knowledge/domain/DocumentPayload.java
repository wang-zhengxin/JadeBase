package ai.jadebase.knowledge.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "document_payloads")
public class DocumentPayload {

    @Id
    private UUID documentId;
    private String filename;
    private String contentType;
    @Column(columnDefinition = "BYTEA")
    private byte[] content;

    protected DocumentPayload() {
    }

    public DocumentPayload(UUID documentId, String filename, String contentType, byte[] content) {
        this.documentId = documentId;
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
    }

    public UUID getDocumentId() { return documentId; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
}
