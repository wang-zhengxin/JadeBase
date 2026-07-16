package ai.jadebase.workspace.api;

import ai.jadebase.workspace.domain.WorkspaceSettings;
import ai.jadebase.workspace.domain.WorkspaceSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/settings")
public class WorkspaceSettingsController {

    private final WorkspaceSettingsService service;

    public WorkspaceSettingsController(WorkspaceSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public SettingsResponse get() {
        return SettingsResponse.from(service.get());
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody UpdateSettings request) {
        WorkspaceSettings settings = service.update(new WorkspaceSettings.Preferences(
                request.profileName(), request.workRole(), request.colorMode(), request.chatBackground(),
                request.language(), request.topK(), request.showCitations(), request.autoScroll(),
                request.smoothStreaming(), request.collapseLargePastes(), request.personalInstructions(),
                request.referenceMemories(), request.updateMemories()));
        return SettingsResponse.from(settings);
    }

    public record UpdateSettings(
            @Size(max = 40) String profileName,
            @Size(max = 60) String workRole,
            @NotNull WorkspaceSettings.ColorMode colorMode,
            @NotNull WorkspaceSettings.ChatBackground chatBackground,
            @NotNull WorkspaceSettings.Language language,
            @Min(1) @Max(12) int topK,
            boolean showCitations,
            boolean autoScroll,
            boolean smoothStreaming,
            boolean collapseLargePastes,
            @Size(max = 500) String personalInstructions,
            boolean referenceMemories,
            boolean updateMemories) { }

    public record SettingsResponse(String profileName, String workRole, String colorMode,
                                   String chatBackground, String language, int topK,
                                   boolean showCitations, boolean autoScroll, boolean smoothStreaming,
                                   boolean collapseLargePastes, String personalInstructions,
                                   boolean referenceMemories, boolean updateMemories, Instant updatedAt) {
        static SettingsResponse from(WorkspaceSettings settings) {
            return new SettingsResponse(settings.getProfileName(), settings.getWorkRole(),
                    value(settings.getColorMode()), value(settings.getChatBackground()),
                    settings.getLanguage() == WorkspaceSettings.Language.ZH_CN ? "zh-CN" : "en",
                    settings.getTopK(), settings.isShowCitations(), settings.isAutoScroll(),
                    settings.isSmoothStreaming(), settings.isCollapseLargePastes(),
                    settings.getPersonalInstructions(), settings.isReferenceMemories(),
                    settings.isUpdateMemories(), settings.getUpdatedAt());
        }

        private static String value(Enum<?> value) {
            return value.name().toLowerCase();
        }
    }
}
