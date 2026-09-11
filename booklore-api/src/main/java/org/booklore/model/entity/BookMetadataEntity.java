package org.booklore.model.entity;

import org.booklore.util.BookUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookMetadataEntity {

    private Long bookId;

    private String title;

    private String subtitle;

    private String publisher;

    private LocalDate publishedDate;

    private String description;

    private String seriesName;

    private Float seriesNumber;

    private Integer seriesTotal;

    private String isbn13;

    private String isbn10;

    private Integer pageCount;

    private String language;

    private Double rating;

    private Integer reviewCount;

    private Instant coverUpdatedOn;

    private Instant audiobookCoverUpdatedOn;

    private Double amazonRating;

    private Integer amazonReviewCount;

    private Double goodreadsRating;

    private Integer goodreadsReviewCount;

    private Double hardcoverRating;

    private Integer hardcoverReviewCount;

    private String asin;

    private String goodreadsId;

    private String hardcoverId;

    private String hardcoverBookId;

    private String googleId;

    private String comicvineId;

    private String lubimyczytacId;

    private Double lubimyczytacRating;

    private String ranobedbId;

    private Double ranobedbRating;

    private String audibleId;

    private Double audibleRating;

    private Integer audibleReviewCount;

    @Builder.Default
    private Boolean titleLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean subtitleLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean publisherLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean publishedDateLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean descriptionLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean isbn13Locked = Boolean.FALSE;

    @Builder.Default
    private Boolean isbn10Locked = Boolean.FALSE;

    @Builder.Default
    private Boolean asinLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean pageCountLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean languageLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean amazonRatingLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean amazonReviewCountLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean goodreadsRatingLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean goodreadsReviewCountLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean hardcoverRatingLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean hardcoverReviewCountLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean coverLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean audiobookCoverLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean seriesNameLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean seriesNumberLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean seriesTotalLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean authorsLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean categoriesLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean moodsLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean tagsLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean goodreadsIdLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean hardcoverIdLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean hardcoverBookIdLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean googleIdLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean comicvineIdLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean lubimyczytacIdLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean lubimyczytacRatingLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean ranobedbIdLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean ranobedbRatingLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean audibleIdLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean audibleRatingLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean audibleReviewCountLocked = Boolean.FALSE;

    private String narrator;

    private Boolean abridged;

    @Builder.Default
    private Boolean reviewsLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean narratorLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean abridgedLocked = Boolean.FALSE;

    private String embeddingVector;

    private Instant embeddingUpdatedAt;

    private String searchText;

    private Integer ageRating;

    private String contentRating;

    @Builder.Default
    private Boolean ageRatingLocked = Boolean.FALSE;

    @Builder.Default
    private Boolean contentRatingLocked = Boolean.FALSE;

    public void updateSearchText() {
        trimStringFields();
        this.searchText = BookUtils.buildSearchText(this);
    }

    private void trimStringFields() {
        this.title = trimOrNull(this.title);
        this.subtitle = trimOrNull(this.subtitle);
        this.publisher = trimOrNull(this.publisher);
        this.seriesName = trimOrNull(this.seriesName);
        this.language = trimOrNull(this.language);
        this.isbn13 = trimOrNull(this.isbn13);
        this.isbn10 = trimOrNull(this.isbn10);
        this.asin = trimOrNull(this.asin);
        this.goodreadsId = trimOrNull(this.goodreadsId);
        this.hardcoverId = trimOrNull(this.hardcoverId);
        this.hardcoverBookId = trimOrNull(this.hardcoverBookId);
        this.googleId = trimOrNull(this.googleId);
        this.comicvineId = trimOrNull(this.comicvineId);
        this.lubimyczytacId = trimOrNull(this.lubimyczytacId);
        this.ranobedbId = trimOrNull(this.ranobedbId);
        this.audibleId = trimOrNull(this.audibleId);
        this.contentRating = trimOrNull(this.contentRating);
        this.narrator = trimOrNull(this.narrator);
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @JsonIgnore
    private BookEntity book;

    private ComicMetadataEntity comicMetadata;

    /**
     * See moodNames/categoryNames/tagNames: same same-request scratch cache, not persisted.
     * Also read by updateSearchText() via BookUtils.buildSearchText, so whoever writes authors via
     * jOOQ must populate this before triggering a save in the same request (mirrors the pre-existing
     * behavior: mutating the old Hibernate collection didn't dirty-check automatically either, hence
     * the manual updateSearchText() calls already present at every writer).
     */
    private List<String> authorNames;

    /** See moodNames: same same-request scratch cache, not persisted. */
    private Set<String> categoryNames;

    /**
     * Mood names for this book, read/written via JooqBookMetadataRelationsRepository (moods is no
     * longer a Hibernate-managed collection). This field is a same-request scratch cache, NOT
     * persisted: MetadataChangeDetector's generic FieldDescriptor lambdas expect an entity getter, so
     * callers populate this once (a single jOOQ read) before diffing/updating moods, instead of each
     * call site re-querying. Never read this without populating it first in the same request.
     */
    private Set<String> moodNames;

    /** See moodNames/categoryNames: same same-request scratch cache, not persisted. */
    private Set<String> tagNames;

    public void applyLockToAllFields(boolean lock) {
        this.titleLocked = lock;
        this.subtitleLocked = lock;
        this.publisherLocked = lock;
        this.publishedDateLocked = lock;
        this.descriptionLocked = lock;
        this.isbn13Locked = lock;
        this.isbn10Locked = lock;
        this.asinLocked = lock;
        this.pageCountLocked = lock;
        this.languageLocked = lock;
        this.coverLocked = lock;
        this.audiobookCoverLocked = lock;
        this.seriesNameLocked = lock;
        this.seriesNumberLocked = lock;
        this.seriesTotalLocked = lock;
        this.authorsLocked = lock;
        this.categoriesLocked = lock;
        this.moodsLocked = lock;
        this.tagsLocked = lock;
        this.amazonRatingLocked = lock;
        this.amazonReviewCountLocked = lock;
        this.goodreadsRatingLocked = lock;
        this.goodreadsReviewCountLocked = lock;
        this.hardcoverRatingLocked = lock;
        this.hardcoverReviewCountLocked = lock;
        this.lubimyczytacRatingLocked = lock;
        this.comicvineIdLocked = lock;
        this.goodreadsIdLocked = lock;
        this.hardcoverIdLocked = lock;
        this.hardcoverBookIdLocked = lock;
        this.googleIdLocked = lock;
        this.lubimyczytacIdLocked = lock;
        this.ranobedbIdLocked = lock;
        this.ranobedbRatingLocked = lock;
        this.audibleIdLocked = lock;
        this.audibleRatingLocked = lock;
        this.audibleReviewCountLocked = lock;
        this.reviewsLocked = lock;
        this.narratorLocked = lock;
        this.abridgedLocked = lock;
        this.ageRatingLocked = lock;
        this.contentRatingLocked = lock;
        if (this.comicMetadata != null) {
            this.comicMetadata.applyLockToAllFields(lock);
        }
    }

    public boolean areAllFieldsLocked() {
        return Boolean.TRUE.equals(this.titleLocked)
                && Boolean.TRUE.equals(this.subtitleLocked)
                && Boolean.TRUE.equals(this.publisherLocked)
                && Boolean.TRUE.equals(this.publishedDateLocked)
                && Boolean.TRUE.equals(this.descriptionLocked)
                && Boolean.TRUE.equals(this.isbn13Locked)
                && Boolean.TRUE.equals(this.isbn10Locked)
                && Boolean.TRUE.equals(this.asinLocked)
                && Boolean.TRUE.equals(this.pageCountLocked)
                && Boolean.TRUE.equals(this.languageLocked)
                && Boolean.TRUE.equals(this.coverLocked)
                && Boolean.TRUE.equals(this.audiobookCoverLocked)
                && Boolean.TRUE.equals(this.seriesNameLocked)
                && Boolean.TRUE.equals(this.seriesNumberLocked)
                && Boolean.TRUE.equals(this.seriesTotalLocked)
                && Boolean.TRUE.equals(this.authorsLocked)
                && Boolean.TRUE.equals(this.categoriesLocked)
                && Boolean.TRUE.equals(this.moodsLocked)
                && Boolean.TRUE.equals(this.tagsLocked)
                && Boolean.TRUE.equals(this.amazonRatingLocked)
                && Boolean.TRUE.equals(this.amazonReviewCountLocked)
                && Boolean.TRUE.equals(this.goodreadsRatingLocked)
                && Boolean.TRUE.equals(this.goodreadsReviewCountLocked)
                && Boolean.TRUE.equals(this.hardcoverRatingLocked)
                && Boolean.TRUE.equals(this.hardcoverReviewCountLocked)
                && Boolean.TRUE.equals(this.lubimyczytacRatingLocked)
                && Boolean.TRUE.equals(this.goodreadsIdLocked)
                && Boolean.TRUE.equals(this.comicvineIdLocked)
                && Boolean.TRUE.equals(this.hardcoverIdLocked)
                && Boolean.TRUE.equals(this.hardcoverBookIdLocked)
                && Boolean.TRUE.equals(this.googleIdLocked)
                && Boolean.TRUE.equals(this.lubimyczytacIdLocked)
                && Boolean.TRUE.equals(this.ranobedbIdLocked)
                && Boolean.TRUE.equals(this.ranobedbRatingLocked)
                && Boolean.TRUE.equals(this.audibleIdLocked)
                && Boolean.TRUE.equals(this.audibleRatingLocked)
                && Boolean.TRUE.equals(this.audibleReviewCountLocked)
                && Boolean.TRUE.equals(this.reviewsLocked)
                && Boolean.TRUE.equals(this.narratorLocked)
                && Boolean.TRUE.equals(this.abridgedLocked)
                && Boolean.TRUE.equals(this.ageRatingLocked)
                && Boolean.TRUE.equals(this.contentRatingLocked)
                && (this.comicMetadata == null || this.comicMetadata.areAllFieldsLocked())
                ;
    }
}
