package ai.jadebase.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_messages",
        indexes = @Index(name = "idx_message_conversation_created", columnList = "conversation_id,created_at"))
public class ConversationMessage {

    public enum Role { USER, ASSISTANT }

    @Id
    @GeneratedValue
    private UUID id;
    @Column(name = "conversation_id")
    private UUID conversationId;
    @Enumerated(EnumType.STRING)
    private Role role;
    @Column(columnDefinition = "TEXT")
    private String content;
    private String mode;
    @Column(name = "reasoning_content", columnDefinition = "TEXT")
    private String reasoningContent;
    @Column(name = "thinking_duration_ms")
    private long thinkingDurationMs;
    @Column(name = "thinking_steps")
    private int thinkingSteps;
    @Column(name = "created_at")
    private Instant createdAt;

    protected ConversationMessage() {
    }

    public ConversationMessage(UUID conversationId, Role role, String content, String mode) {
        this(conversationId, role, content, mode, null, 0, 0);
    }

    public ConversationMessage(UUID conversationId, Role role, String content, String mode,
                               String reasoningContent, long thinkingDurationMs, int thinkingSteps) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.mode = mode;
        this.reasoningContent = reasoningContent;
        this.thinkingDurationMs = thinkingDurationMs;
        this.thinkingSteps = thinkingSteps;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public String getMode() { return mode; }
    public String getReasoningContent() { return reasoningContent; }
    public long getThinkingDurationMs() { return thinkingDurationMs; }
    public int getThinkingSteps() { return thinkingSteps; }
    public Instant getCreatedAt() { return createdAt; }
}
