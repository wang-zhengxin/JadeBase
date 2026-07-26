package ai.jadebase.knowledge.infra;

import ai.jadebase.knowledge.domain.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {
    List<KnowledgeBase> findTop6ByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrderByCreatedAtDesc(
            String name, String description);
}
