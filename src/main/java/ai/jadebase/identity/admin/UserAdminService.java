package ai.jadebase.identity.admin;

import ai.jadebase.identity.domain.IdentityAccessException;
import ai.jadebase.identity.domain.IdentityConflictException;
import ai.jadebase.identity.domain.JadeUser;
import ai.jadebase.identity.infra.AuthSessionRepository;
import ai.jadebase.identity.infra.JadeUserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserAdminService {

    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final JadeUserRepository users;
    private final AuthSessionRepository sessions;
    private final UserInvitationRepository invitations;
    private final WorkspaceAccessSettingsRepository accessSettings;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserAdminService(JadeUserRepository users, AuthSessionRepository sessions,
                            UserInvitationRepository invitations,
                            WorkspaceAccessSettingsRepository accessSettings) {
        this.users = users;
        this.sessions = sessions;
        this.invitations = invitations;
        this.accessSettings = accessSettings;
    }

    @Transactional(readOnly = true)
    public UserPageView list(String rawQuery, String rawRole, String rawStatus, int requestedPage, int requestedSize) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        JadeUser.Role role = parseOptionalRole(rawRole);
        JadeUser.Status status = parseOptionalStatus(rawStatus);
        int page = Math.max(0, requestedPage);
        int size = Math.min(100, Math.max(1, requestedSize));
        List<UserView> filtered = users.findAllByOrderByUpdatedAtDesc().stream()
                .filter(user -> query.isBlank() || user.getEmail().toLowerCase(Locale.ROOT).contains(query)
                        || user.getDisplayName().toLowerCase(Locale.ROOT).contains(query))
                .filter(user -> role == null || user.getRole() == role)
                .filter(user -> status == null || user.getStatus() == status)
                .map(UserView::from)
                .toList();
        int from = Math.min(filtered.size(), page * size);
        int to = Math.min(filtered.size(), from + size);
        List<InvitationView> pending = pendingInvitations().stream().map(InvitationView::from).toList();
        int totalPages = filtered.isEmpty() ? 1 : (filtered.size() + size - 1) / size;
        return new UserPageView(filtered.subList(from, to), pending, filtered.size(), totalPages, page, size,
                users.countByStatus(JadeUser.Status.ACTIVE), pending.size(), settings().isRestrictOpenSignup());
    }

    @Transactional
    public CreatedInvitation invite(JadeUser actor, InviteInput input) {
        String email = normalizeEmail(input.email());
        if (users.existsByEmail(email)) throw new IdentityConflictException("该邮箱已经是工作区成员");
        JadeUser.Role role = parseRole(input.role(), JadeUser.Role.MEMBER);
        List<UserInvitation> previous = invitations.findByEmailAndAcceptedAtIsNull(email);
        if (!previous.isEmpty()) invitations.deleteAll(previous);

        String token = newToken();
        UserInvitation invitation = invitations.save(new UserInvitation(
                email, role, hashToken(token), actor, Instant.now().plus(INVITATION_TTL)));
        return new CreatedInvitation(InvitationView.from(invitation), token);
    }

    @Transactional
    public void revokeInvitation(UUID invitationId) {
        UserInvitation invitation = invitations.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("邀请不存在"));
        if (invitation.getAcceptedAt() != null) throw new IllegalStateException("已接受的邀请不能撤销");
        invitations.delete(invitation);
    }

    @Transactional
    public UserView updateUser(JadeUser actor, UUID userId, UpdateUserInput input) {
        JadeUser target = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        JadeUser.Role nextRole = parseRole(input.role(), target.getRole());
        JadeUser.Status nextStatus = parseStatus(input.status(), target.getStatus());
        if (target.getId().equals(actor.getId()) && nextStatus == JadeUser.Status.SUSPENDED) {
            throw new IllegalStateException("不能停用当前登录账号");
        }
        if (target.getId().equals(actor.getId()) && nextRole != JadeUser.Role.OWNER) {
            throw new IllegalStateException("不能降级当前登录的所有者账号");
        }
        boolean removesActiveOwner = target.getRole() == JadeUser.Role.OWNER
                && target.getStatus() == JadeUser.Status.ACTIVE
                && (nextRole != JadeUser.Role.OWNER || nextStatus != JadeUser.Status.ACTIVE);
        if (removesActiveOwner && users.countByRoleAndStatus(JadeUser.Role.OWNER, JadeUser.Status.ACTIVE) <= 1) {
            throw new IllegalStateException("工作区必须保留至少一个有效所有者");
        }
        if (target.getRole() != nextRole) target.changeRole(nextRole);
        if (target.getStatus() != nextStatus) {
            target.changeStatus(nextStatus);
            if (nextStatus == JadeUser.Status.SUSPENDED) sessions.deleteByUser_Id(target.getId());
        }
        return UserView.from(users.save(target));
    }

    @Transactional
    public AccessPolicyView updateAccessPolicy(boolean restricted) {
        WorkspaceAccessSettings settings = settings();
        settings.update(restricted);
        return AccessPolicyView.from(accessSettings.save(settings), false, null);
    }

    @Transactional(readOnly = true)
    public AccessPolicyView registrationPolicy(String rawToken) {
        boolean restricted = users.count() > 0 && settings().isRestrictOpenSignup();
        UserInvitation invitation = validInvitation(rawToken);
        return AccessPolicyView.from(settings(), invitation != null, invitation == null ? null : invitation.getEmail())
                .withRestricted(restricted);
    }

    @Transactional
    public JadeUser.Role claimRegistration(String email, String rawToken) {
        if (users.count() == 0) return JadeUser.Role.OWNER;
        UserInvitation invitation = validInvitation(rawToken);
        if (invitation != null) {
            if (!invitation.getEmail().equals(email)) {
                throw new IdentityAccessException("请使用被邀请的邮箱地址注册");
            }
            invitation.accept();
            invitations.save(invitation);
            return invitation.getRole();
        }
        if (rawToken != null && !rawToken.isBlank()) throw new IdentityAccessException("邀请链接无效或已过期");
        if (settings().isRestrictOpenSignup()) throw new IdentityAccessException("当前工作区仅允许受邀用户注册");
        return JadeUser.Role.MEMBER;
    }

    private List<UserInvitation> pendingInvitations() {
        return invitations.findByAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Instant.now());
    }

    private UserInvitation validInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        return invitations.findByTokenHash(hashToken(rawToken.trim()))
                .filter(invitation -> invitation.isPending(Instant.now()))
                .orElse(null);
    }

    private WorkspaceAccessSettings settings() {
        return accessSettings.findById(1L).orElseGet(() -> accessSettings.save(new WorkspaceAccessSettings(false)));
    }

    private JadeUser.Role parseOptionalRole(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        return parseRole(value, null);
    }

    private JadeUser.Status parseOptionalStatus(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        return parseStatus(value, null);
    }

    private JadeUser.Role parseRole(String value, JadeUser.Role fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return JadeUser.Role.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("不支持的用户角色"); }
    }

    private JadeUser.Status parseStatus(String value, JadeUser.Status fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return JadeUser.Status.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("不支持的用户状态"); }
    }

    private String normalizeEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank() || email.length() > 320 || !email.contains("@")) {
            throw new IllegalArgumentException("请输入有效的邮箱地址");
        }
        return email;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    public record InviteInput(String email, String role) { }
    public record UpdateUserInput(String role, String status) { }

    public record UserView(UUID id, String email, String displayName, String role, String status,
                           Instant createdAt, Instant updatedAt, Instant lastLoginAt) {
        static UserView from(JadeUser user) {
            return new UserView(user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getRole().name().toLowerCase(Locale.ROOT),
                    user.getStatus().name().toLowerCase(Locale.ROOT), user.getCreatedAt(),
                    user.getUpdatedAt(), user.getLastLoginAt());
        }
    }

    public record InvitationView(UUID id, String email, String role, String invitedBy,
                                 Instant expiresAt, Instant createdAt) {
        static InvitationView from(UserInvitation invitation) {
            String inviter = invitation.getInvitedBy().getDisplayName().isBlank()
                    ? invitation.getInvitedBy().getEmail() : invitation.getInvitedBy().getDisplayName();
            return new InvitationView(invitation.getId(), invitation.getEmail(),
                    invitation.getRole().name().toLowerCase(Locale.ROOT), inviter,
                    invitation.getExpiresAt(), invitation.getCreatedAt());
        }
    }

    public record CreatedInvitation(InvitationView invitation, String token) { }
    public record UserPageView(List<UserView> users, List<InvitationView> invitations, long totalElements,
                               int totalPages, int page, int size, long activeUsers, long pendingInvites,
                               boolean restrictOpenSignup) { }

    public record AccessPolicyView(boolean restrictOpenSignup, boolean invitationValid, String invitationEmail) {
        static AccessPolicyView from(WorkspaceAccessSettings settings, boolean invitationValid, String email) {
            return new AccessPolicyView(settings.isRestrictOpenSignup(), invitationValid, email);
        }

        AccessPolicyView withRestricted(boolean restricted) {
            return new AccessPolicyView(restricted, invitationValid, invitationEmail);
        }
    }
}
