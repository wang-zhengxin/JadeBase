package ai.jadebase.identity.admin;

import ai.jadebase.identity.domain.JadeUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_invitations")
public class UserInvitation {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JadeUser.Role role;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private JadeUser invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserInvitation() { }

    public UserInvitation(String email, JadeUser.Role role, String tokenHash, JadeUser invitedBy, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public void accept() {
        this.acceptedAt = Instant.now();
    }

    public boolean isPending(Instant now) {
        return acceptedAt == null && expiresAt.isAfter(now);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public JadeUser.Role getRole() { return role; }
    public String getTokenHash() { return tokenHash; }
    public JadeUser getInvitedBy() { return invitedBy; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
