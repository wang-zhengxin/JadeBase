package ai.jadebase.identity.infra;

import ai.jadebase.identity.domain.JadeUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JadeUserRepository extends JpaRepository<JadeUser, UUID> {
    Optional<JadeUser> findByEmail(String email);
    boolean existsByEmail(String email);
}
