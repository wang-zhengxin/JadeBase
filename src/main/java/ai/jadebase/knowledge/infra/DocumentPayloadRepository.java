package ai.jadebase.knowledge.infra;

import ai.jadebase.knowledge.domain.DocumentPayload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentPayloadRepository extends JpaRepository<DocumentPayload, UUID> {
}
