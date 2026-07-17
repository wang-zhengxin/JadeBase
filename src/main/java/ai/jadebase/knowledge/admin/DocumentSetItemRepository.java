package ai.jadebase.knowledge.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentSetItemRepository extends JpaRepository<DocumentSetItem, UUID> {
    List<DocumentSetItem> findByDocumentSetIdOrderByCreatedAtAsc(UUID documentSetId);
    void deleteByDocumentSetId(UUID documentSetId);
}
