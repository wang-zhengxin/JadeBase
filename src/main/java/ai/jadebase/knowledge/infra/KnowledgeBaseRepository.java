package ai.jadebase.knowledge.infra;

import ai.jadebase.knowledge.domain.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {
}
