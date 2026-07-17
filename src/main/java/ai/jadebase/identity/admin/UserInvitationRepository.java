package ai.jadebase.identity.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {
    Optional<UserInvitation> findByTokenHash(String tokenHash);
    List<UserInvitation> findByEmailAndAcceptedAtIsNull(String email);
    List<UserInvitation> findByAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Instant now);
}
