package ai.jadebase.knowledge.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentSetRepository extends JpaRepository<DocumentSet, UUID> {
    List<DocumentSet> findAllByOrderByUpdatedAtDesc();
}
