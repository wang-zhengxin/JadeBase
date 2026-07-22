package ai.jadebase.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_runs")
public class AgentRun {

    public enum Status { RUNNING, COMPLETED, FAILED }

    @Id
    private UUID id;
    @Column(name = "agent_id", nullable = false)
    private UUID agentId;
    @Column(name = "agent_version", nullable = false)
    private int agentVersion;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "conversation_id")
    private UUID conversationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;
    @Column(nullable = false)
    private String question;
    private String answer;
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected AgentRun() { }

    public AgentRun(UUID agentId, int agentVersion, UUID userId, String question) {
        this.id = UUID.randomUUID();
        this.agentId = agentId;
        this.agentVersion = agentVersion;
        this.userId = userId;
        this.question = question;
        this.status = Status.RUNNING;
        this.durationMs = 0;
        this.startedAt = Instant.now();
    }

    public void complete(UUID conversationId, String answer) {
        this.conversationId = conversationId;
        this.answer = answer;
        finish(Status.COMPLETED);
    }

    public void fail(String message) {
        this.errorMessage = message == null ? "Agent 执行失败" : message.substring(0, Math.min(1000, message.length()));
        finish(Status.FAILED);
    }

    private void finish(Status result) {
        this.status = result;
        this.completedAt = Instant.now();
        this.durationMs = Duration.between(startedAt, completedAt).toMillis();
    }

    public UUID getId() { return id; }
    public UUID getAgentId() { return agentId; }
    public int getAgentVersion() { return agentVersion; }
    public UUID getUserId() { return userId; }
    public UUID getConversationId() { return conversationId; }
    public Status getStatus() { return status; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public String getErrorMessage() { return errorMessage; }
    public long getDurationMs() { return durationMs; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
