package org.booklore.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComicMetadataEntity {

    private Long bookId;

    @JsonIgnore
    private BookMetadataEntity bookMetadata;

    private String issueNumber;

    private String volumeName;

    private Integer volumeNumber;

    private String storyArc;

    private Integer storyArcNumber;

    private String alternateSeries;

    private String alternateIssue;

    private String imprint;

    private String format;

    @Builder.Default
    private Boolean blackAndWhite = Boolean.FALSE;

    @Builder.Default
    private Boolean manga = Boolean.FALSE;

    @Builder.Default
    private String readingDirection = "ltr";

    private String webLink;

    private String notes;

    // characters/teams/locations/creatorMappings are no longer Hibernate-managed collections;
    // these scratch fields exist purely so MetadataChangeDetector's generic FieldDescriptor lambdas
    // and BookMetadataUpdater's diffing keep an entity-getter shape, same pattern as
    // BookMetadataEntity's authorNames/categoryNames/moodNames/tagNames.
    // Populated via JooqComicMetadataRepository at the point of use, not automatically.
    private Set<String> characterNames;

    private Set<String> teamNames;

    private Set<String> locationNames;

    private Set<String> pencillerNames;

    private Set<String> inkerNames;

    private Set<String> coloristNames;

    private Set<String> lettererNames;

    private Set<String> coverArtistNames;

    private Set<String> editorNames;

    // Locked fields
    @Builder.Default
    private Boolean issueNumberLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean volumeNameLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean volumeNumberLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean storyArcLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean storyArcNumberLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean alternateSeriesLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean alternateIssueLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean imprintLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean formatLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean blackAndWhiteLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean mangaLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean readingDirectionLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean webLinkLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean notesLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean creatorsLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean pencillersLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean inkersLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean coloristsLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean letterersLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean coverArtistsLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean editorsLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean charactersLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean teamsLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean locationsLocked = Boolean.FALSE;

    public void applyLockToAllFields(boolean lock) {
        this.issueNumberLocked = lock;
        this.volumeNameLocked = lock;
        this.volumeNumberLocked = lock;
        this.storyArcLocked = lock;
        this.storyArcNumberLocked = lock;
        this.alternateSeriesLocked = lock;
        this.alternateIssueLocked = lock;
        this.imprintLocked = lock;
        this.formatLocked = lock;
        this.blackAndWhiteLocked = lock;
        this.mangaLocked = lock;
        this.readingDirectionLocked = lock;
        this.webLinkLocked = lock;
        this.notesLocked = lock;
        this.creatorsLocked = lock;
        this.pencillersLocked = lock;
        this.inkersLocked = lock;
        this.coloristsLocked = lock;
        this.letterersLocked = lock;
        this.coverArtistsLocked = lock;
        this.editorsLocked = lock;
        this.charactersLocked = lock;
        this.teamsLocked = lock;
        this.locationsLocked = lock;
    }

    public boolean areAllFieldsLocked() {
        return Boolean.TRUE.equals(this.issueNumberLocked)
                && Boolean.TRUE.equals(this.volumeNameLocked)
                && Boolean.TRUE.equals(this.volumeNumberLocked)
                && Boolean.TRUE.equals(this.storyArcLocked)
                && Boolean.TRUE.equals(this.storyArcNumberLocked)
                && Boolean.TRUE.equals(this.alternateSeriesLocked)
                && Boolean.TRUE.equals(this.alternateIssueLocked)
                && Boolean.TRUE.equals(this.imprintLocked)
                && Boolean.TRUE.equals(this.formatLocked)
                && Boolean.TRUE.equals(this.blackAndWhiteLocked)
                && Boolean.TRUE.equals(this.mangaLocked)
                && Boolean.TRUE.equals(this.readingDirectionLocked)
                && Boolean.TRUE.equals(this.webLinkLocked)
                && Boolean.TRUE.equals(this.notesLocked)
                && Boolean.TRUE.equals(this.creatorsLocked)
                && Boolean.TRUE.equals(this.pencillersLocked)
                && Boolean.TRUE.equals(this.inkersLocked)
                && Boolean.TRUE.equals(this.coloristsLocked)
                && Boolean.TRUE.equals(this.letterersLocked)
                && Boolean.TRUE.equals(this.coverArtistsLocked)
                && Boolean.TRUE.equals(this.editorsLocked)
                && Boolean.TRUE.equals(this.charactersLocked)
                && Boolean.TRUE.equals(this.teamsLocked)
                && Boolean.TRUE.equals(this.locationsLocked);
    }
}
