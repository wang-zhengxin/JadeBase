package ai.jadebase.identity.infra;

import ai.jadebase.identity.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Query("select session from AuthSession session join fetch session.user "
            + "where session.tokenHash = :tokenHash and session.expiresAt > :now")
    Optional<AuthSession> findActive(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
    void deleteByTokenHash(String tokenHash);
    long deleteByExpiresAtBefore(Instant now);
}
