package ai.jadebase.identity.domain;

import ai.jadebase.identity.admin.UserAdminService;
import ai.jadebase.identity.infra.AuthSessionRepository;
import ai.jadebase.identity.infra.JadeUserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    public static final String SESSION_COOKIE = "JADEBASE_SESSION";
    public static final Duration SESSION_TTL = Duration.ofDays(30);

    private final JadeUserRepository users;
    private final AuthSessionRepository sessions;
    private final UserAdminService userAdminService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(JadeUserRepository users, AuthSessionRepository sessions, UserAdminService userAdminService) {
        this.users = users;
        this.sessions = sessions;
        this.userAdminService = userAdminService;
    }

    @Transactional
    public SessionResult register(String rawEmail, String password, String inviteToken) {
        String email = normalizeEmail(rawEmail);
        validatePassword(password);
        if (users.existsByEmail(email)) throw new IdentityConflictException("该邮箱已注册");
        JadeUser.Role role = userAdminService.claimRegistration(email, inviteToken);
        JadeUser user = users.save(new JadeUser(email, passwordEncoder.encode(password), role));
        return createSession(user);
    }

    @Transactional
    public SessionResult login(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        JadeUser user = users.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("邮箱或密码错误"));
        if (user.getStatus() != JadeUser.Status.ACTIVE) throw new AuthenticationException("该账号已被停用");
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw new AuthenticationException("邮箱或密码错误");
        }
        return createSession(user);
    }

    @Transactional(readOnly = true)
    public JadeUser authenticate(String token) {
        if (token == null || token.isBlank()) throw new AuthenticationException("请先登录");
        JadeUser user = sessions.findActive(hashToken(token), Instant.now())
                .map(AuthSession::getUser)
                .orElseThrow(() -> new AuthenticationException("登录已过期，请重新登录"));
        if (user.getStatus() != JadeUser.Status.ACTIVE) throw new AuthenticationException("该账号已被停用");
        return user;
    }

    @Transactional
    public JadeUser updateDisplayName(UUID userId, String displayName) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.length() > 40) throw new IllegalArgumentException("显示名称不能超过 40 个字符");
        JadeUser user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        user.updateDisplayName(normalized);
        return users.save(user);
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        JadeUser user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw new AuthenticationException("当前密码不正确");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        users.save(user);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) sessions.deleteByTokenHash(hashToken(token));
    }

    private SessionResult createSession(JadeUser user) {
        sessions.deleteByExpiresAtBefore(Instant.now());
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        user.recordLogin();
        users.save(user);
        sessions.save(new AuthSession(user, hashToken(token), expiresAt));
        return new SessionResult(user, token, expiresAt);
    }

    private String normalizeEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank() || email.length() > 320 || !email.contains("@")) {
            throw new IllegalArgumentException("请输入有效的邮箱地址");
        }
        return email;
    }

    private void validatePassword(String password) {
        int length = password == null ? 0 : password.length();
        if (length < 8 || length > 72) throw new IllegalArgumentException("密码长度必须为 8 到 72 个字符");
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    public record SessionResult(JadeUser user, String token, Instant expiresAt) { }

    @Transactional(readOnly = true)
    public UserAdminService.AccessPolicyView registrationPolicy(String inviteToken) {
        return userAdminService.registrationPolicy(inviteToken);
    }
}
