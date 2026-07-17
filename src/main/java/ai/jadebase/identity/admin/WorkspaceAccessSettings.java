package ai.jadebase.identity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "workspace_access_settings")
public class WorkspaceAccessSettings {

    @Id
    private Long id;

    @Column(name = "restrict_open_signup", nullable = false)
    private boolean restrictOpenSignup;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceAccessSettings() { }

    public WorkspaceAccessSettings(boolean restrictOpenSignup) {
        this.id = 1L;
        this.restrictOpenSignup = restrictOpenSignup;
        this.updatedAt = Instant.now();
    }

    public void update(boolean restricted) {
        this.restrictOpenSignup = restricted;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public boolean isRestrictOpenSignup() { return restrictOpenSignup; }
    public Instant getUpdatedAt() { return updatedAt; }
}
