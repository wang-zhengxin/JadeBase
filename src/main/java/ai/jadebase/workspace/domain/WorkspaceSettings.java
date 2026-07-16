package ai.jadebase.workspace.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "workspace_settings")
public class WorkspaceSettings {

    public enum ColorMode { AUTO, LIGHT, DARK }
    public enum ChatBackground { NONE, FOG, SAGE, GRAPHITE }
    public enum Language { ZH_CN, EN }

    @Id
    private Long id;
    private String profileName;
    private String workRole;
    @Enumerated(EnumType.STRING)
    private ColorMode colorMode;
    @Enumerated(EnumType.STRING)
    private ChatBackground chatBackground;
    @Enumerated(EnumType.STRING)
    private Language language;
    private int topK;
    private boolean showCitations;
    private Instant updatedAt;

    protected WorkspaceSettings() {
    }

    public WorkspaceSettings(long id) {
        this.id = id;
        this.profileName = "";
        this.workRole = "";
        this.colorMode = ColorMode.AUTO;
        this.chatBackground = ChatBackground.NONE;
        this.language = Language.ZH_CN;
        this.topK = 6;
        this.showCitations = true;
        this.updatedAt = Instant.now();
    }

    public void update(String profileName, String workRole, ColorMode colorMode,
                       ChatBackground chatBackground, Language language, int topK,
                       boolean showCitations) {
        this.profileName = profileName == null ? "" : profileName.trim();
        this.workRole = workRole == null ? "" : workRole.trim();
        this.colorMode = colorMode;
        this.chatBackground = chatBackground;
        this.language = language;
        this.topK = topK;
        this.showCitations = showCitations;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getProfileName() { return profileName; }
    public String getWorkRole() { return workRole; }
    public ColorMode getColorMode() { return colorMode; }
    public ChatBackground getChatBackground() { return chatBackground; }
    public Language getLanguage() { return language; }
    public int getTopK() { return topK; }
    public boolean isShowCitations() { return showCitations; }
    public Instant getUpdatedAt() { return updatedAt; }
}
