package ai.jadebase.workspace.infra;

import ai.jadebase.workspace.domain.WorkspaceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceSettingsRepository extends JpaRepository<WorkspaceSettings, Long> {
}
