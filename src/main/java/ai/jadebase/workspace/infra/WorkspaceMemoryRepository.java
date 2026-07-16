package ai.jadebase.workspace.infra;

import ai.jadebase.workspace.domain.WorkspaceMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemoryRepository extends JpaRepository<WorkspaceMemory, UUID> {
    List<WorkspaceMemory> findAllByOrderByCreatedAtDesc();
    Optional<WorkspaceMemory> findByContent(String content);
}
