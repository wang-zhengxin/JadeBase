package ai.jadebase.workspace.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
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
    private boolean autoScroll;
    private boolean smoothStreaming;
    private boolean collapseLargePastes;
    @Column(length = 500)
    private String personalInstructions;
    private boolean referenceMemories;
    private boolean updateMemories;
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
        this.autoScroll = true;
        this.smoothStreaming = true;
        this.collapseLargePastes = true;
        this.personalInstructions = "";
        this.referenceMemories = true;
        this.updateMemories = false;
        this.updatedAt = Instant.now();
    }

    public void update(Preferences preferences) {
        this.profileName = preferences.profileName() == null ? "" : preferences.profileName().trim();
        this.workRole = preferences.workRole() == null ? "" : preferences.workRole().trim();
        this.colorMode = preferences.colorMode();
        this.chatBackground = preferences.chatBackground();
        this.language = preferences.language();
        this.topK = preferences.topK();
        this.showCitations = preferences.showCitations();
        this.autoScroll = preferences.autoScroll();
        this.smoothStreaming = preferences.smoothStreaming();
        this.collapseLargePastes = preferences.collapseLargePastes();
        this.personalInstructions = preferences.personalInstructions() == null
                ? "" : preferences.personalInstructions().trim();
        this.referenceMemories = preferences.referenceMemories();
        this.updateMemories = preferences.updateMemories();
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
    public boolean isAutoScroll() { return autoScroll; }
    public boolean isSmoothStreaming() { return smoothStreaming; }
    public boolean isCollapseLargePastes() { return collapseLargePastes; }
    public String getPersonalInstructions() { return personalInstructions; }
    public boolean isReferenceMemories() { return referenceMemories; }
    public boolean isUpdateMemories() { return updateMemories; }
    public Instant getUpdatedAt() { return updatedAt; }

    public record Preferences(String profileName, String workRole, ColorMode colorMode,
                              ChatBackground chatBackground, Language language, int topK,
                              boolean showCitations, boolean autoScroll, boolean smoothStreaming,
                              boolean collapseLargePastes, String personalInstructions,
                              boolean referenceMemories, boolean updateMemories) { }
}
