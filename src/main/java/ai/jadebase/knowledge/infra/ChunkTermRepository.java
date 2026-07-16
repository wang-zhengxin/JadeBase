package ai.jadebase.knowledge.infra;

import ai.jadebase.knowledge.domain.ChunkTerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChunkTermRepository extends JpaRepository<ChunkTerm, UUID> {
    void deleteByDocumentId(UUID documentId);
}
