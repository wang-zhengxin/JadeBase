package ai.jadebase.workspace.domain;

import ai.jadebase.workspace.infra.WorkspaceSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceSettingsService {

    private static final long DEFAULT_WORKSPACE = 1L;
    private final WorkspaceSettingsRepository repository;

    public WorkspaceSettingsService(WorkspaceSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WorkspaceSettings get() {
        return repository.findById(DEFAULT_WORKSPACE)
                .orElseGet(() -> repository.save(new WorkspaceSettings(DEFAULT_WORKSPACE)));
    }

    @Transactional
    public WorkspaceSettings update(String profileName, String workRole,
                                    WorkspaceSettings.ColorMode colorMode,
                                    WorkspaceSettings.ChatBackground chatBackground,
                                    WorkspaceSettings.Language language, int topK,
                                    boolean showCitations) {
        if (topK < 1 || topK > 12) throw new IllegalArgumentException("召回片段数必须在 1 到 12 之间");
        WorkspaceSettings settings = get();
        settings.update(profileName, workRole, colorMode, chatBackground, language, topK, showCitations);
        return repository.save(settings);
    }
}
