package org.booklore.service.metadata;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.booklore.config.AppProperties;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.dto.FileMoveResult;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.*;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.booklore.util.MetadataChangeDetector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataUpdater {

    private final org.booklore.repository.jooq.JooqBookMetadataRelationsRepository relationsRepository;
    private final org.booklore.repository.jooq.JooqBookRepository jooqBookRepository;
    private final org.booklore.repository.jooq.JooqBookMetadataRepository jooqBookMetadataRepository;
    private final org.booklore.repository.jooq.JooqComicMetadataRepository comicRelationsRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final AppProperties appProperties;
    private final FileService fileService;
    private final MetadataMatchService metadataMatchService;
    private final AppSettingService appSettingService;
    private final MetadataWriterFactory metadataWriterFactory;
    private final BookReviewUpdateService bookReviewUpdateService;
    private final FileMoveService fileMoveService;
    private final SidecarMetadataWriter sidecarMetadataWriter;

    @Transactional
    public void setBookMetadata(MetadataUpdateContext context) {
        BookEntity bookEntity = context.getBookEntity();
        MetadataUpdateWrapper wrapper = context.getMetadataUpdateWrapper();
        boolean mergeCategories = context.isMergeCategories();
        boolean mergeMoods = context.isMergeMoods();
        boolean mergeTags = context.isMergeTags();
        boolean updateThumbnail = context.isUpdateThumbnail();
        MetadataReplaceMode replaceMode = context.getReplaceMode();

        Long bookId = bookEntity.getId();
        BookMetadata newMetadata = wrapper.getMetadata();

        if (newMetadata == null) {
            log.warn("Metadata is null for book ID {}. Skipping update.", bookId);
            return;
        }

        MetadataClearFlags clearFlags = wrapper.getClearFlags();
        BookMetadataEntity metadata = bookEntity.getMetadata();
        // MetadataChangeDetector's moods/categories/tags fields read the transient scratch caches
        // (not Hibernate collections) - populate them once here via jOOQ.
        metadata.setMoodNames(relationsRepository.findMoodNamesByBookId(bookId));
        metadata.setCategoryNames(relationsRepository.findCategoryNamesByBookId(bookId));
        metadata.setTagNames(relationsRepository.findTagNamesByBookId(bookId));
        metadata.setAuthorNames(relationsRepository.findAuthorNamesByBookId(bookId));
        // MetadataChangeDetector is also a @UtilityClass (no DI), so comic relation names must be
        // snapshotted here too, before it runs, not lazily inside updateComicMetadataIfNeeded.
        if (metadata.getComicMetadata() != null) {
            populateComicRelationNames(metadata.getComicMetadata());
        }

        boolean thumbnailRequiresUpdate = StringUtils.hasText(newMetadata.getThumbnailUrl());
        boolean hasMetadataChanges = MetadataChangeDetector.isDifferent(newMetadata, metadata, clearFlags);
        boolean hasValueChanges = MetadataChangeDetector.hasValueChanges(newMetadata, metadata, clearFlags);
        if (!thumbnailRequiresUpdate && !hasMetadataChanges) {
            log.info("No changes in metadata for book ID {}. Skipping update.", bookId);
            return;
        }

        boolean hasLockChanges = MetadataChangeDetector.hasLockChanges(newMetadata, metadata);
        if (metadata.areAllFieldsLocked() && hasValueChanges && !hasLockChanges) {
            log.warn("All fields are locked for book ID {}. Skipping update.", bookId);
            return;
        }

        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        MetadataPersistenceSettings.SaveToOriginalFile writeToFile = settings.getSaveToOriginalFile();
        var primaryFile = bookEntity.getPrimaryBookFile();
        BookFileType bookType = primaryFile != null ? primaryFile.getBookType() : null;

        boolean hasValueChangesForFileWrite = MetadataChangeDetector.hasValueChangesForFileWrite(newMetadata, metadata, clearFlags);

        updateBasicFields(newMetadata, metadata, clearFlags, replaceMode);
        updateAuthorsIfNeeded(newMetadata, metadata, clearFlags, mergeCategories, replaceMode);
        updateCategoriesIfNeeded(newMetadata, metadata, clearFlags, mergeCategories, replaceMode);
        updateMoodsIfNeeded(newMetadata, metadata, clearFlags, mergeMoods, replaceMode);
        updateTagsIfNeeded(newMetadata, metadata, clearFlags, mergeTags, replaceMode);
        bookReviewUpdateService.updateBookReviews(newMetadata, metadata, clearFlags, mergeCategories);
        updateThumbnailIfNeeded(bookId, bookEntity, newMetadata, metadata, updateThumbnail, bookType);
        updateAudiobookMetadataIfNeeded(bookEntity, newMetadata, metadata, clearFlags, replaceMode);
        updateComicMetadataIfNeeded(newMetadata, metadata, replaceMode);
        updateLocks(newMetadata, metadata);

        Instant metadataUpdatedAt = Instant.now();
        bookEntity.setMetadataUpdatedAt(metadataUpdatedAt);
        metadata.updateSearchText();
        jooqBookMetadataRepository.updateFullRow(metadata);
        jooqBookRepository.updateMetadataUpdatedAt(bookId, metadataUpdatedAt);
        try {
            Float score = metadataMatchService.calculateMatchScore(bookEntity);
            bookEntity.setMetadataMatchScore(score);
            jooqBookRepository.updateMetadataMatchScore(bookId, score);
        } catch (Exception e) {
            log.warn("Failed to calculate metadata match score for book ID {}: {}", bookId, e.getMessage());
        }

        if (appProperties.isLocalStorage() && primaryFile != null && bookType != null && ((writeToFile.isAnyFormatEnabled() && hasValueChangesForFileWrite) || thumbnailRequiresUpdate)) {
            metadataWriterFactory.getWriter(bookType).ifPresent(writer -> {
                try {
                    String thumbnailUrl = updateThumbnail ? newMetadata.getThumbnailUrl() : null;
                    if ((StringUtils.hasText(thumbnailUrl) && isLocalOrPrivateUrl(thumbnailUrl) || Boolean.TRUE.equals(metadata.getCoverLocked()))) {
                        log.debug("Blocked local/private thumbnail URL: {}", thumbnailUrl);
                        thumbnailUrl = null;
                    }
                    File file = new File(bookEntity.getFullFilePath().toUri());
                    writer.saveMetadataToFile(file, metadata, thumbnailUrl, clearFlags);
                    updateFileNameIfConverted(primaryFile, file.toPath());
                    String newHash = file.isDirectory()
                            ? FileFingerprint.generateFolderHash(bookEntity.getFullFilePath())
                            : FileFingerprint.generateHash(bookEntity.getFullFilePath());
                    Instant metadataForWriteUpdatedAt = Instant.now();
                    bookEntity.setMetadataForWriteUpdatedAt(metadataForWriteUpdatedAt);
                    primaryFile.setCurrentHash(newHash);
                    jooqBookRepository.updateBookFileHash(primaryFile.getId(), newHash);
                    jooqBookRepository.updateMetadataForWriteUpdatedAt(bookId, metadataForWriteUpdatedAt);
                } catch (Exception e) {
                    log.warn("Failed to write metadata for book ID {}: {}", bookId, e.getMessage());
                }
            });
        }

        if (sidecarMetadataWriter.isWriteOnUpdateEnabled()) {
            try {
                sidecarMetadataWriter.writeSidecarMetadata(bookEntity);
            } catch (Exception e) {
                log.warn("Failed to write sidecar metadata for book ID {}: {}", bookId, e.getMessage());
            }
        }

        boolean moveFilesToLibraryPattern = settings.isMoveFilesToLibraryPattern();
        if (moveFilesToLibraryPattern && primaryFile != null) {
            try {
                BookEntity book = metadata.getBook();
                FileMoveResult result = fileMoveService.moveSingleFile(book);
                if (result.isMoved()) {
                    var bookPrimaryFile = book.getPrimaryBookFile();
                    if (bookPrimaryFile != null) {
                        bookPrimaryFile.setFileName(result.getNewFileName());
                        bookPrimaryFile.setFileSubPath(result.getNewFileSubPath());
                        bookAdditionalFileRepository.updateFileNameAndSubPath(
                                bookPrimaryFile.getId(), result.getNewFileName(), result.getNewFileSubPath());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to move files for book ID {} after metadata update: {}", bookId, e.getMessage());
            }
        }
    }

    private void updateBasicFields(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, MetadataReplaceMode replaceMode) {
        if (clear == null) {
            clear = new MetadataClearFlags();
        }
        handleFieldUpdate(e.getTitleLocked(), clear.isTitle(), m.getTitle(), v -> e.setTitle(nullIfBlank(v)), e::getTitle, replaceMode);
        handleFieldUpdate(e.getSubtitleLocked(), clear.isSubtitle(), m.getSubtitle(), v -> e.setSubtitle(nullIfBlank(v)), e::getSubtitle, replaceMode);
        handleFieldUpdate(e.getPublisherLocked(), clear.isPublisher(), m.getPublisher(), v -> e.setPublisher(nullIfBlank(v)), e::getPublisher, replaceMode);
        handleFieldUpdate(e.getPublishedDateLocked(), clear.isPublishedDate(), m.getPublishedDate(), e::setPublishedDate, e::getPublishedDate, replaceMode);
        handleFieldUpdate(e.getDescriptionLocked(), clear.isDescription(), m.getDescription(), v -> e.setDescription(nullIfBlank(v)), e::getDescription, replaceMode);
        handleFieldUpdate(e.getSeriesNameLocked(), clear.isSeriesName(), m.getSeriesName(), e::setSeriesName, e::getSeriesName, replaceMode);
        handleFieldUpdate(e.getSeriesNumberLocked(), clear.isSeriesNumber(), m.getSeriesNumber(), e::setSeriesNumber, e::getSeriesNumber, replaceMode);
        handleFieldUpdate(e.getSeriesTotalLocked(), clear.isSeriesTotal(), m.getSeriesTotal(), e::setSeriesTotal, e::getSeriesTotal, replaceMode);
        handleFieldUpdate(e.getIsbn13Locked(), clear.isIsbn13(), m.getIsbn13(), v -> e.setIsbn13(nullIfBlank(v)), e::getIsbn13, replaceMode);
        handleFieldUpdate(e.getIsbn10Locked(), clear.isIsbn10(), m.getIsbn10(), v -> e.setIsbn10(nullIfBlank(v)), e::getIsbn10, replaceMode);
        handleFieldUpdate(e.getAsinLocked(), clear.isAsin(), m.getAsin(), v -> e.setAsin(nullIfBlank(v)), e::getAsin, replaceMode);
        handleFieldUpdate(e.getGoodreadsIdLocked(), clear.isGoodreadsId(), m.getGoodreadsId(), v -> e.setGoodreadsId(nullIfBlank(v)), e::getGoodreadsId, replaceMode);
        handleFieldUpdate(e.getComicvineIdLocked(), clear.isComicvineId(), m.getComicvineId(), v -> e.setComicvineId(nullIfBlank(v)), e::getComicvineId, replaceMode);
        handleFieldUpdate(e.getHardcoverIdLocked(), clear.isHardcoverId(), m.getHardcoverId(), v -> e.setHardcoverId(nullIfBlank(v)), e::getHardcoverId, replaceMode);
        handleFieldUpdate(e.getHardcoverBookIdLocked(), clear.isHardcoverBookId(), m.getHardcoverBookId(), e::setHardcoverBookId, e::getHardcoverBookId, replaceMode);
        handleFieldUpdate(e.getGoogleIdLocked(), clear.isGoogleId(), m.getGoogleId(), v -> e.setGoogleId(nullIfBlank(v)), e::getGoogleId, replaceMode);
        handleFieldUpdate(e.getPageCountLocked(), clear.isPageCount(), m.getPageCount(), e::setPageCount, e::getPageCount, replaceMode);
        handleFieldUpdate(e.getLanguageLocked(), clear.isLanguage(), m.getLanguage(), v -> e.setLanguage(nullIfBlank(v)), e::getLanguage, replaceMode);
        handleFieldUpdate(e.getAmazonRatingLocked(), clear.isAmazonRating(), m.getAmazonRating(), e::setAmazonRating, e::getAmazonRating, replaceMode);
        handleFieldUpdate(e.getAmazonReviewCountLocked(), clear.isAmazonReviewCount(), m.getAmazonReviewCount(), e::setAmazonReviewCount, e::getAmazonReviewCount, replaceMode);
        handleFieldUpdate(e.getGoodreadsRatingLocked(), clear.isGoodreadsRating(), m.getGoodreadsRating(), e::setGoodreadsRating, e::getGoodreadsRating, replaceMode);
        handleFieldUpdate(e.getGoodreadsReviewCountLocked(), clear.isGoodreadsReviewCount(), m.getGoodreadsReviewCount(), e::setGoodreadsReviewCount, e::getGoodreadsReviewCount, replaceMode);
        handleFieldUpdate(e.getHardcoverRatingLocked(), clear.isHardcoverRating(), m.getHardcoverRating(), e::setHardcoverRating, e::getHardcoverRating, replaceMode);
        handleFieldUpdate(e.getHardcoverReviewCountLocked(), clear.isHardcoverReviewCount(), m.getHardcoverReviewCount(), e::setHardcoverReviewCount, e::getHardcoverReviewCount, replaceMode);
        handleFieldUpdate(e.getLubimyczytacIdLocked(), clear.isLubimyczytacId(), m.getLubimyczytacId(), v -> e.setLubimyczytacId(nullIfBlank(v)), () -> e.getLubimyczytacId(), replaceMode);
        handleFieldUpdate(e.getLubimyczytacRatingLocked(), clear.isLubimyczytacRating(), m.getLubimyczytacRating(), v -> e.setLubimyczytacRating(v), () -> e.getLubimyczytacRating(), replaceMode);
        handleFieldUpdate(e.getRanobedbIdLocked(), clear.isRanobedbId(), m.getRanobedbId(), v -> e.setRanobedbId(nullIfBlank(v)), e::getRanobedbId, replaceMode);
        handleFieldUpdate(e.getRanobedbRatingLocked(), clear.isRanobedbRating(), m.getRanobedbRating(), e::setRanobedbRating, e::getRanobedbRating, replaceMode);
        handleFieldUpdate(e.getAudibleIdLocked(), clear.isAudibleId(), m.getAudibleId(), v -> e.setAudibleId(nullIfBlank(v)), e::getAudibleId, replaceMode);
        handleFieldUpdate(e.getAudibleRatingLocked(), clear.isAudibleRating(), m.getAudibleRating(), e::setAudibleRating, e::getAudibleRating, replaceMode);
        handleFieldUpdate(e.getAudibleReviewCountLocked(), clear.isAudibleReviewCount(), m.getAudibleReviewCount(), e::setAudibleReviewCount, e::getAudibleReviewCount, replaceMode);
        handleFieldUpdate(e.getAgeRatingLocked(), clear.isAgeRating(), m.getAgeRating(), e::setAgeRating, e::getAgeRating, replaceMode);
        handleFieldUpdate(e.getContentRatingLocked(), clear.isContentRating(), m.getContentRating(), v -> e.setContentRating(nullIfBlank(v)), e::getContentRating, replaceMode);
    }

    private <T> void handleFieldUpdate(Boolean locked, boolean shouldClear, T newValue, Consumer<T> setter, Supplier<T> getter, MetadataReplaceMode mode) {
        if (Boolean.TRUE.equals(locked)) return;
        if (shouldClear) {
            setter.accept(null);
            return;
        }
        if (mode == null) {
            if (newValue != null) setter.accept(newValue);
            return;
        }
        switch (mode) {
            case REPLACE_ALL -> setter.accept(newValue);
            case REPLACE_MISSING -> {
                if (isValueMissing(getter.get())) setter.accept(newValue);
            }
            case REPLACE_WHEN_PROVIDED -> {
                if (!isValueMissing(newValue)) setter.accept(newValue);
            }
        }
    }

    private <T> boolean isValueMissing(T value) {
        if (value == null) return true;
        if (value instanceof String) return !StringUtils.hasText((String) value);
        return false;
    }

    /**
     * Writes authors via jOOQ instead of mutating a Hibernate collection: e.getAuthorNames() is the
     * pre-update snapshot (ordered) populated once at the top of setBookMetadata. Every substantive
     * branch computes the FINAL ordered name list and calls relationsRepository.setAuthorsForBook
     * (which handles per-name dedup and sort_order) exactly once, keeps e.getAuthorNames() in sync,
     * and calls e.updateSearchText() only when authors actually change, mirroring prior behavior.
     */
    private void updateAuthorsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getAuthorsLocked())) return;

        List<String> existingAuthors = Optional.ofNullable(e.getAuthorNames()).orElse(Collections.emptyList());

        if (clear.isAuthors()) {
            relationsRepository.clearAuthorsForBook(e.getBookId());
            e.setAuthorNames(Collections.emptyList());
            return;
        }

        List<String> authorNames = Optional.ofNullable(m.getAuthors()).orElse(Collections.emptyList());
        if (authorNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
                relationsRepository.clearAuthorsForBook(e.getBookId());
                e.setAuthorNames(Collections.emptyList());
            }
            return;
        }

        List<String> newAuthors = authorNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .toList();

        if (newAuthors.isEmpty()) return;

        List<String> finalAuthors;
        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED || replaceMode == null) {
            if (merge) {
                List<String> combined = new ArrayList<>(existingAuthors);
                for (String name : newAuthors) {
                    if (!combined.contains(name)) combined.add(name);
                }
                finalAuthors = combined;
            } else {
                finalAuthors = newAuthors;
            }
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && existingAuthors.isEmpty()) {
            finalAuthors = newAuthors;
        } else {
            return;
        }

        relationsRepository.setAuthorsForBook(e.getBookId(), finalAuthors);
        e.setAuthorNames(finalAuthors);
        e.updateSearchText();
    }

    /** Same jOOQ-write pattern as updateMoodsIfNeeded; see its doc comment. */
    private void updateCategoriesIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getCategoriesLocked())) return;

        Set<String> existingCategories = Optional.ofNullable(e.getCategoryNames()).orElse(Collections.emptySet());

        if (clear.isCategories()) {
            relationsRepository.clearCategoriesForBook(e.getBookId());
            e.setCategoryNames(Collections.emptySet());
            return;
        }

        Set<String> categoryNames = Optional.ofNullable(m.getCategories()).orElse(Collections.emptySet());
        if (categoryNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
                relationsRepository.clearCategoriesForBook(e.getBookId());
                e.setCategoryNames(Collections.emptySet());
            }
            return;
        }

        Set<String> newCategories = categoryNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());

        if (newCategories.isEmpty()) return;

        Set<String> finalCategories;
        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED || replaceMode == null) {
            if (merge) {
                finalCategories = new HashSet<>(existingCategories);
                finalCategories.addAll(newCategories);
            } else {
                finalCategories = newCategories;
            }
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && existingCategories.isEmpty()) {
            finalCategories = newCategories;
        } else {
            return;
        }

        relationsRepository.setCategoriesForBook(e.getBookId(), finalCategories);
        e.setCategoryNames(finalCategories);
    }

    /**
     * Writes moods via jOOQ instead of mutating a Hibernate collection: e.getMoodNames() is the
     * pre-update snapshot populated once at the top of updateMetadata, and every branch below computes
     * the FINAL desired name set and calls relationsRepository.setMoodsForBook/clearMoodsForBook exactly
     * once, keeping e.getMoodNames() in sync so later steps in the same request see the new value.
     */
    private void updateMoodsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getMoodsLocked())) return;

        Set<String> existingMoods = Optional.ofNullable(e.getMoodNames()).orElse(Collections.emptySet());

        if (clear.isMoods()) {
            relationsRepository.clearMoodsForBook(e.getBookId());
            e.setMoodNames(Collections.emptySet());
            return;
        }

        Set<String> moodNames = Optional.ofNullable(m.getMoods()).orElse(Collections.emptySet());
        if (moodNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
                relationsRepository.clearMoodsForBook(e.getBookId());
                e.setMoodNames(Collections.emptySet());
            }
            return;
        }

        Set<String> newMoods = moodNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());

        if (newMoods.isEmpty()) return;

        Set<String> finalMoods;
        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED || replaceMode == null) {
            if (merge) {
                finalMoods = new HashSet<>(existingMoods);
                finalMoods.addAll(newMoods);
            } else {
                finalMoods = newMoods;
            }
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && existingMoods.isEmpty()) {
            finalMoods = newMoods;
        } else {
            return;
        }

        relationsRepository.setMoodsForBook(e.getBookId(), finalMoods);
        e.setMoodNames(finalMoods);
    }

    /** Same jOOQ-write pattern as updateMoodsIfNeeded; see its doc comment. */
    private void updateTagsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getTagsLocked())) return;

        Set<String> existingTags = Optional.ofNullable(e.getTagNames()).orElse(Collections.emptySet());

        if (clear.isTags()) {
            relationsRepository.clearTagsForBook(e.getBookId());
            e.setTagNames(Collections.emptySet());
            return;
        }

        Set<String> tagNames = Optional.ofNullable(m.getTags()).orElse(Collections.emptySet());
        if (tagNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
                relationsRepository.clearTagsForBook(e.getBookId());
                e.setTagNames(Collections.emptySet());
            }
            return;
        }

        Set<String> newTags = tagNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());

        if (newTags.isEmpty()) return;

        Set<String> finalTags;
        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED || replaceMode == null) {
            if (merge) {
                finalTags = new HashSet<>(existingTags);
                finalTags.addAll(newTags);
            } else {
                finalTags = newTags;
            }
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && existingTags.isEmpty()) {
            finalTags = newTags;
        } else {
            return;
        }

        relationsRepository.setTagsForBook(e.getBookId(), finalTags);
        e.setTagNames(finalTags);
    }

    private void updateAudiobookMetadataIfNeeded(BookEntity bookEntity, BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, MetadataReplaceMode replaceMode) {
        if (clear == null) {
            clear = new MetadataClearFlags();
        }
        handleFieldUpdate(e.getNarratorLocked(), clear.isNarrator(), m.getNarrator(), v -> e.setNarrator(nullIfBlank(v)), e::getNarrator, replaceMode);
        handleFieldUpdate(e.getAbridgedLocked(), clear.isAbridged(), m.getAbridged(), e::setAbridged, e::getAbridged, replaceMode);
    }

    private void updateComicMetadataIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataReplaceMode replaceMode) {
        ComicMetadata comicDto = m.getComicMetadata();
        if (comicDto == null) {
            return;
        }

        ComicMetadataEntity comic = e.getComicMetadata();
        if (comic == null) {
            if (!hasComicData(comicDto) && !hasComicLocks(comicDto)) {
                return;
            }
            comic = ComicMetadataEntity.builder()
                    .bookId(e.getBookId())
                    .build();
            e.setComicMetadata(comic);
            // Unlike JPA's saveAndFlush (needed because comic_metadata.book_id is manually assigned,
            // not identity-generated, so a plain save() could defer the INSERT past the relation-table
            // FK writes below), a jOOQ INSERT executes immediately - no flush-timing gotcha.
            comicRelationsRepository.insertBlankRow(e.getBookId());
            // Brand new row: nothing to snapshot from jOOQ, every relation starts empty.
            comic.setCharacterNames(Collections.emptySet());
            comic.setTeamNames(Collections.emptySet());
            comic.setLocationNames(Collections.emptySet());
            comic.setPencillerNames(Collections.emptySet());
            comic.setInkerNames(Collections.emptySet());
            comic.setColoristNames(Collections.emptySet());
            comic.setLettererNames(Collections.emptySet());
            comic.setCoverArtistNames(Collections.emptySet());
            comic.setEditorNames(Collections.emptySet());
        }

        ComicMetadataEntity c = comic;

        // Update basic fields
        handleFieldUpdate(c.getIssueNumberLocked(), false, comicDto.getIssueNumber(), v -> c.setIssueNumber(nullIfBlank(v)), c::getIssueNumber, replaceMode);
        handleFieldUpdate(c.getVolumeNameLocked(), false, comicDto.getVolumeName(), v -> c.setVolumeName(nullIfBlank(v)), c::getVolumeName, replaceMode);
        handleFieldUpdate(c.getVolumeNumberLocked(), false, comicDto.getVolumeNumber(), c::setVolumeNumber, c::getVolumeNumber, replaceMode);
        handleFieldUpdate(c.getStoryArcLocked(), false, comicDto.getStoryArc(), v -> c.setStoryArc(nullIfBlank(v)), c::getStoryArc, replaceMode);
        handleFieldUpdate(c.getStoryArcNumberLocked(), false, comicDto.getStoryArcNumber(), c::setStoryArcNumber, c::getStoryArcNumber, replaceMode);
        handleFieldUpdate(c.getAlternateSeriesLocked(), false, comicDto.getAlternateSeries(), v -> c.setAlternateSeries(nullIfBlank(v)), c::getAlternateSeries, replaceMode);
        handleFieldUpdate(c.getAlternateIssueLocked(), false, comicDto.getAlternateIssue(), v -> c.setAlternateIssue(nullIfBlank(v)), c::getAlternateIssue, replaceMode);
        handleFieldUpdate(c.getImprintLocked(), false, comicDto.getImprint(), v -> c.setImprint(nullIfBlank(v)), c::getImprint, replaceMode);
        handleFieldUpdate(c.getFormatLocked(), false, comicDto.getFormat(), v -> c.setFormat(nullIfBlank(v)), c::getFormat, replaceMode);
        handleFieldUpdate(c.getBlackAndWhiteLocked(), false, comicDto.getBlackAndWhite(), c::setBlackAndWhite, c::getBlackAndWhite, replaceMode);
        handleFieldUpdate(c.getMangaLocked(), false, comicDto.getManga(), c::setManga, c::getManga, replaceMode);
        handleFieldUpdate(c.getReadingDirectionLocked(), false, comicDto.getReadingDirection(), v -> c.setReadingDirection(nullIfBlank(v)), c::getReadingDirection, replaceMode);
        handleFieldUpdate(c.getWebLinkLocked(), false, comicDto.getWebLink(), v -> c.setWebLink(nullIfBlank(v)), c::getWebLink, replaceMode);
        handleFieldUpdate(c.getNotesLocked(), false, comicDto.getNotes(), v -> c.setNotes(nullIfBlank(v)), c::getNotes, replaceMode);

        // Update relationships if not locked
        if (!Boolean.TRUE.equals(c.getCharactersLocked())) {
            updateComicCharacters(c, comicDto.getCharacters(), replaceMode);
        }
        if (!Boolean.TRUE.equals(c.getTeamsLocked())) {
            updateComicTeams(c, comicDto.getTeams(), replaceMode);
        }
        if (!Boolean.TRUE.equals(c.getLocationsLocked())) {
            updateComicLocations(c, comicDto.getLocations(), replaceMode);
        }
        updateComicCreatorsPerRole(c, comicDto, replaceMode);

        // Update locks if provided
        if (comicDto.getIssueNumberLocked() != null) c.setIssueNumberLocked(comicDto.getIssueNumberLocked());
        if (comicDto.getVolumeNameLocked() != null) c.setVolumeNameLocked(comicDto.getVolumeNameLocked());
        if (comicDto.getVolumeNumberLocked() != null) c.setVolumeNumberLocked(comicDto.getVolumeNumberLocked());
        if (comicDto.getStoryArcLocked() != null) c.setStoryArcLocked(comicDto.getStoryArcLocked());
        if (comicDto.getStoryArcNumberLocked() != null) c.setStoryArcNumberLocked(comicDto.getStoryArcNumberLocked());
        if (comicDto.getAlternateSeriesLocked() != null) c.setAlternateSeriesLocked(comicDto.getAlternateSeriesLocked());
        if (comicDto.getAlternateIssueLocked() != null) c.setAlternateIssueLocked(comicDto.getAlternateIssueLocked());
        if (comicDto.getImprintLocked() != null) c.setImprintLocked(comicDto.getImprintLocked());
        if (comicDto.getFormatLocked() != null) c.setFormatLocked(comicDto.getFormatLocked());
        if (comicDto.getBlackAndWhiteLocked() != null) c.setBlackAndWhiteLocked(comicDto.getBlackAndWhiteLocked());
        if (comicDto.getMangaLocked() != null) c.setMangaLocked(comicDto.getMangaLocked());
        if (comicDto.getReadingDirectionLocked() != null) c.setReadingDirectionLocked(comicDto.getReadingDirectionLocked());
        if (comicDto.getWebLinkLocked() != null) c.setWebLinkLocked(comicDto.getWebLinkLocked());
        if (comicDto.getNotesLocked() != null) c.setNotesLocked(comicDto.getNotesLocked());
        if (comicDto.getCreatorsLocked() != null) c.setCreatorsLocked(comicDto.getCreatorsLocked());
        if (comicDto.getPencillersLocked() != null) c.setPencillersLocked(comicDto.getPencillersLocked());
        if (comicDto.getInkersLocked() != null) c.setInkersLocked(comicDto.getInkersLocked());
        if (comicDto.getColoristsLocked() != null) c.setColoristsLocked(comicDto.getColoristsLocked());
        if (comicDto.getLetterersLocked() != null) c.setLetterersLocked(comicDto.getLetterersLocked());
        if (comicDto.getCoverArtistsLocked() != null) c.setCoverArtistsLocked(comicDto.getCoverArtistsLocked());
        if (comicDto.getEditorsLocked() != null) c.setEditorsLocked(comicDto.getEditorsLocked());
        if (comicDto.getCharactersLocked() != null) c.setCharactersLocked(comicDto.getCharactersLocked());
        if (comicDto.getTeamsLocked() != null) c.setTeamsLocked(comicDto.getTeamsLocked());
        if (comicDto.getLocationsLocked() != null) c.setLocationsLocked(comicDto.getLocationsLocked());

        comicRelationsRepository.updateFullRow(c);

        comicRelationsRepository.deleteOrphanedCharacters();
        comicRelationsRepository.deleteOrphanedTeams();
        comicRelationsRepository.deleteOrphanedLocations();
        comicRelationsRepository.deleteOrphanedCreators();
    }

    /**
     * Snapshots a comic metadata row's relation names via jOOQ into its @Transient scratch fields.
     * Called once at the top of setBookMetadata (before MetadataChangeDetector runs, since that's
     * also a @UtilityClass and can't query jOOQ itself) for a comic row that already existed;
     * updateComicMetadataIfNeeded seeds a newly-created row's fields to empty sets directly instead.
     */
    private void populateComicRelationNames(ComicMetadataEntity c) {
        Long bookId = c.getBookId();
        c.setCharacterNames(comicRelationsRepository.findCharacterNamesByBookId(bookId));
        c.setTeamNames(comicRelationsRepository.findTeamNamesByBookId(bookId));
        c.setLocationNames(comicRelationsRepository.findLocationNamesByBookId(bookId));
        c.setPencillerNames(comicRelationsRepository.findCreatorNamesByBookIdAndRole(bookId, ComicCreatorRole.PENCILLER));
        c.setInkerNames(comicRelationsRepository.findCreatorNamesByBookIdAndRole(bookId, ComicCreatorRole.INKER));
        c.setColoristNames(comicRelationsRepository.findCreatorNamesByBookIdAndRole(bookId, ComicCreatorRole.COLORIST));
        c.setLettererNames(comicRelationsRepository.findCreatorNamesByBookIdAndRole(bookId, ComicCreatorRole.LETTERER));
        c.setCoverArtistNames(comicRelationsRepository.findCreatorNamesByBookIdAndRole(bookId, ComicCreatorRole.COVER_ARTIST));
        c.setEditorNames(comicRelationsRepository.findCreatorNamesByBookIdAndRole(bookId, ComicCreatorRole.EDITOR));
    }

    private boolean hasComicData(ComicMetadata dto) {
        return StringUtils.hasText(dto.getIssueNumber())
                || StringUtils.hasText(dto.getVolumeName())
                || dto.getVolumeNumber() != null
                || StringUtils.hasText(dto.getStoryArc())
                || dto.getStoryArcNumber() != null
                || StringUtils.hasText(dto.getAlternateSeries())
                || StringUtils.hasText(dto.getAlternateIssue())
                || StringUtils.hasText(dto.getImprint())
                || StringUtils.hasText(dto.getFormat())
                || dto.getBlackAndWhite() != null
                || dto.getManga() != null
                || StringUtils.hasText(dto.getReadingDirection())
                || StringUtils.hasText(dto.getWebLink())
                || StringUtils.hasText(dto.getNotes())
                || (dto.getCharacters() != null && !dto.getCharacters().isEmpty())
                || (dto.getTeams() != null && !dto.getTeams().isEmpty())
                || (dto.getLocations() != null && !dto.getLocations().isEmpty())
                || (dto.getPencillers() != null && !dto.getPencillers().isEmpty())
                || (dto.getInkers() != null && !dto.getInkers().isEmpty())
                || (dto.getColorists() != null && !dto.getColorists().isEmpty())
                || (dto.getLetterers() != null && !dto.getLetterers().isEmpty())
                || (dto.getCoverArtists() != null && !dto.getCoverArtists().isEmpty())
                || (dto.getEditors() != null && !dto.getEditors().isEmpty());
    }

    private boolean hasComicLocks(ComicMetadata dto) {
        return Boolean.TRUE.equals(dto.getIssueNumberLocked())
                || Boolean.TRUE.equals(dto.getVolumeNameLocked())
                || Boolean.TRUE.equals(dto.getVolumeNumberLocked())
                || Boolean.TRUE.equals(dto.getStoryArcLocked())
                || Boolean.TRUE.equals(dto.getStoryArcNumberLocked())
                || Boolean.TRUE.equals(dto.getAlternateSeriesLocked())
                || Boolean.TRUE.equals(dto.getAlternateIssueLocked())
                || Boolean.TRUE.equals(dto.getImprintLocked())
                || Boolean.TRUE.equals(dto.getFormatLocked())
                || Boolean.TRUE.equals(dto.getBlackAndWhiteLocked())
                || Boolean.TRUE.equals(dto.getMangaLocked())
                || Boolean.TRUE.equals(dto.getReadingDirectionLocked())
                || Boolean.TRUE.equals(dto.getWebLinkLocked())
                || Boolean.TRUE.equals(dto.getNotesLocked())
                || Boolean.TRUE.equals(dto.getCreatorsLocked())
                || Boolean.TRUE.equals(dto.getPencillersLocked())
                || Boolean.TRUE.equals(dto.getInkersLocked())
                || Boolean.TRUE.equals(dto.getColoristsLocked())
                || Boolean.TRUE.equals(dto.getLetterersLocked())
                || Boolean.TRUE.equals(dto.getCoverArtistsLocked())
                || Boolean.TRUE.equals(dto.getEditorsLocked())
                || Boolean.TRUE.equals(dto.getCharactersLocked())
                || Boolean.TRUE.equals(dto.getTeamsLocked())
                || Boolean.TRUE.equals(dto.getLocationsLocked());
    }

    private void updateComicCharacters(ComicMetadataEntity c, Set<String> characters, MetadataReplaceMode mode) {
        Set<String> existing = Optional.ofNullable(c.getCharacterNames()).orElse(Collections.emptySet());
        if (characters == null || characters.isEmpty()) {
            if (mode == MetadataReplaceMode.REPLACE_ALL) {
                comicRelationsRepository.clearCharactersForBook(c.getBookId());
                c.setCharacterNames(Collections.emptySet());
            }
            return;
        }
        if (mode == MetadataReplaceMode.REPLACE_MISSING && !existing.isEmpty()) {
            return;
        }
        // A null replace mode merges with existing (unlike creator roles below, which fully replace).
        Set<String> finalNames;
        if (mode == null) {
            finalNames = new HashSet<>(existing);
            finalNames.addAll(characters);
        } else {
            finalNames = characters;
        }
        comicRelationsRepository.setCharactersForBook(c.getBookId(), finalNames);
        c.setCharacterNames(finalNames);
    }

    private void updateComicTeams(ComicMetadataEntity c, Set<String> teams, MetadataReplaceMode mode) {
        Set<String> existing = Optional.ofNullable(c.getTeamNames()).orElse(Collections.emptySet());
        if (teams == null || teams.isEmpty()) {
            if (mode == MetadataReplaceMode.REPLACE_ALL) {
                comicRelationsRepository.clearTeamsForBook(c.getBookId());
                c.setTeamNames(Collections.emptySet());
            }
            return;
        }
        if (mode == MetadataReplaceMode.REPLACE_MISSING && !existing.isEmpty()) {
            return;
        }
        Set<String> finalNames;
        if (mode == null) {
            finalNames = new HashSet<>(existing);
            finalNames.addAll(teams);
        } else {
            finalNames = teams;
        }
        comicRelationsRepository.setTeamsForBook(c.getBookId(), finalNames);
        c.setTeamNames(finalNames);
    }

    private void updateComicLocations(ComicMetadataEntity c, Set<String> locations, MetadataReplaceMode mode) {
        Set<String> existing = Optional.ofNullable(c.getLocationNames()).orElse(Collections.emptySet());
        if (locations == null || locations.isEmpty()) {
            if (mode == MetadataReplaceMode.REPLACE_ALL) {
                comicRelationsRepository.clearLocationsForBook(c.getBookId());
                c.setLocationNames(Collections.emptySet());
            }
            return;
        }
        if (mode == MetadataReplaceMode.REPLACE_MISSING && !existing.isEmpty()) {
            return;
        }
        Set<String> finalNames;
        if (mode == null) {
            finalNames = new HashSet<>(existing);
            finalNames.addAll(locations);
        } else {
            finalNames = locations;
        }
        comicRelationsRepository.setLocationsForBook(c.getBookId(), finalNames);
        c.setLocationNames(finalNames);
    }

    private void updateComicCreatorsPerRole(ComicMetadataEntity c, ComicMetadata dto, MetadataReplaceMode mode) {
        updateCreatorRole(c, dto.getPencillers(), ComicCreatorRole.PENCILLER, c.getPencillersLocked(), mode);
        updateCreatorRole(c, dto.getInkers(), ComicCreatorRole.INKER, c.getInkersLocked(), mode);
        updateCreatorRole(c, dto.getColorists(), ComicCreatorRole.COLORIST, c.getColoristsLocked(), mode);
        updateCreatorRole(c, dto.getLetterers(), ComicCreatorRole.LETTERER, c.getLetterersLocked(), mode);
        updateCreatorRole(c, dto.getCoverArtists(), ComicCreatorRole.COVER_ARTIST, c.getCoverArtistsLocked(), mode);
        updateCreatorRole(c, dto.getEditors(), ComicCreatorRole.EDITOR, c.getEditorsLocked(), mode);
    }

    private void updateCreatorRole(ComicMetadataEntity c, Set<String> names, ComicCreatorRole role, Boolean locked, MetadataReplaceMode mode) {
        if (Boolean.TRUE.equals(locked)) return;

        boolean hasNewNames = names != null && !names.isEmpty();
        Set<String> existing = existingCreatorNames(c, role);

        if (!hasNewNames) {
            if (mode == MetadataReplaceMode.REPLACE_ALL) {
                comicRelationsRepository.clearCreatorsForBookRole(c.getBookId(), role);
                setCreatorNames(c, role, Collections.emptySet());
            }
            return;
        }

        if (mode == MetadataReplaceMode.REPLACE_MISSING && !existing.isEmpty()) {
            return;
        }

        // REPLACE_ALL / REPLACE_WHEN_PROVIDED / null all fully replace this role's creators -
        // unlike characters/teams/locations, a null replace mode does NOT merge here (matches
        // the pre-jOOQ entity-collection behavior exactly).
        comicRelationsRepository.setCreatorsForBookRole(c.getBookId(), role, names);
        setCreatorNames(c, role, names);
    }

    private Set<String> existingCreatorNames(ComicMetadataEntity c, ComicCreatorRole role) {
        Set<String> names = switch (role) {
            case PENCILLER -> c.getPencillerNames();
            case INKER -> c.getInkerNames();
            case COLORIST -> c.getColoristNames();
            case LETTERER -> c.getLettererNames();
            case COVER_ARTIST -> c.getCoverArtistNames();
            case EDITOR -> c.getEditorNames();
        };
        return names != null ? names : Collections.emptySet();
    }

    private void setCreatorNames(ComicMetadataEntity c, ComicCreatorRole role, Set<String> names) {
        switch (role) {
            case PENCILLER -> c.setPencillerNames(names);
            case INKER -> c.setInkerNames(names);
            case COLORIST -> c.setColoristNames(names);
            case LETTERER -> c.setLettererNames(names);
            case COVER_ARTIST -> c.setCoverArtistNames(names);
            case EDITOR -> c.setEditorNames(names);
        }
    }

    private void updateThumbnailIfNeeded(long bookId, BookEntity bookEntity, BookMetadata m, BookMetadataEntity e, boolean set, BookFileType bookType) {
        if (Boolean.TRUE.equals(e.getCoverLocked())) {
            return;
        }
        if (!set) return;
        if (!StringUtils.hasText(m.getThumbnailUrl()) || isLocalOrPrivateUrl(m.getThumbnailUrl())) return;
        try {
            if (bookType == BookFileType.AUDIOBOOK) {
                if (Boolean.TRUE.equals(e.getAudiobookCoverLocked())) return;
                fileService.createAudiobookThumbnailFromUrl(bookId, m.getThumbnailUrl());
                bookEntity.getMetadata().setAudiobookCoverUpdatedOn(Instant.now());
            } else {
                fileService.createThumbnailFromUrl(bookId, m.getThumbnailUrl());
                bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
            }
            String coverHash = BookCoverUtils.generateCoverHash();
            bookEntity.setBookCoverHash(coverHash);
            jooqBookRepository.updateBookCoverHash(bookId, coverHash);
        } catch (Exception ex) {
            log.warn("Failed to download cover for book {}: {}", bookId, ex.getMessage());
        }
    }

    private void updateLocks(BookMetadata m, BookMetadataEntity e) {
        List<Pair<Boolean, Consumer<Boolean>>> lockMappings = List.of(
                Pair.of(m.getTitleLocked(), e::setTitleLocked),
                Pair.of(m.getSubtitleLocked(), e::setSubtitleLocked),
                Pair.of(m.getPublisherLocked(), e::setPublisherLocked),
                Pair.of(m.getPublishedDateLocked(), e::setPublishedDateLocked),
                Pair.of(m.getDescriptionLocked(), e::setDescriptionLocked),
                Pair.of(m.getSeriesNameLocked(), e::setSeriesNameLocked),
                Pair.of(m.getSeriesNumberLocked(), e::setSeriesNumberLocked),
                Pair.of(m.getSeriesTotalLocked(), e::setSeriesTotalLocked),
                Pair.of(m.getIsbn13Locked(), e::setIsbn13Locked),
                Pair.of(m.getIsbn10Locked(), e::setIsbn10Locked),
                Pair.of(m.getAsinLocked(), e::setAsinLocked),
                Pair.of(m.getGoodreadsIdLocked(), e::setGoodreadsIdLocked),
                Pair.of(m.getComicvineIdLocked(), e::setComicvineIdLocked),
                Pair.of(m.getHardcoverIdLocked(), e::setHardcoverIdLocked),
                Pair.of(m.getHardcoverBookIdLocked(), e::setHardcoverBookIdLocked),
                Pair.of(m.getLubimyczytacIdLocked(), e::setLubimyczytacIdLocked),
                Pair.of(m.getLubimyczytacRatingLocked(), e::setLubimyczytacRatingLocked),
                Pair.of(m.getGoogleIdLocked(), e::setGoogleIdLocked),
                Pair.of(m.getPageCountLocked(), e::setPageCountLocked),
                Pair.of(m.getLanguageLocked(), e::setLanguageLocked),
                Pair.of(m.getAmazonRatingLocked(), e::setAmazonRatingLocked),
                Pair.of(m.getAmazonReviewCountLocked(), e::setAmazonReviewCountLocked),
                Pair.of(m.getGoodreadsRatingLocked(), e::setGoodreadsRatingLocked),
                Pair.of(m.getGoodreadsReviewCountLocked(), e::setGoodreadsReviewCountLocked),
                Pair.of(m.getHardcoverRatingLocked(), e::setHardcoverRatingLocked),
                Pair.of(m.getHardcoverReviewCountLocked(), e::setHardcoverReviewCountLocked),
                Pair.of(m.getRanobedbIdLocked(), e::setRanobedbIdLocked),
                Pair.of(m.getRanobedbRatingLocked(), e::setRanobedbRatingLocked),
                Pair.of(m.getAudibleIdLocked(), e::setAudibleIdLocked),
                Pair.of(m.getAudibleRatingLocked(), e::setAudibleRatingLocked),
                Pair.of(m.getAudibleReviewCountLocked(), e::setAudibleReviewCountLocked),
                Pair.of(m.getCoverLocked(), e::setCoverLocked),
                Pair.of(m.getAudiobookCoverLocked(), e::setAudiobookCoverLocked),
                Pair.of(m.getAuthorsLocked(), e::setAuthorsLocked),
                Pair.of(m.getCategoriesLocked(), e::setCategoriesLocked),
                Pair.of(m.getMoodsLocked(), e::setMoodsLocked),
                Pair.of(m.getTagsLocked(), e::setTagsLocked),
                Pair.of(m.getReviewsLocked(), e::setReviewsLocked),
                Pair.of(m.getNarratorLocked(), e::setNarratorLocked),
                Pair.of(m.getAbridgedLocked(), e::setAbridgedLocked),
                Pair.of(m.getAgeRatingLocked(), e::setAgeRatingLocked),
                Pair.of(m.getContentRatingLocked(), e::setContentRatingLocked)
        );
        lockMappings.forEach(pair -> {
            if (pair.getLeft() != null) pair.getRight().accept(pair.getLeft());
        });
    }

    private String nullIfBlank(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    void updateFileNameIfConverted(BookFileEntity bookFile, Path originalPath) {
        if (Files.exists(originalPath)) {
            return;
        }
        String fileName = bookFile.getFileName();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String cbzFileName = baseName + ".cbz";
        Path cbzPath = originalPath.resolveSibling(cbzFileName);
        if (Files.exists(cbzPath)) {
            log.info("File converted from {} to {}, updating book file record", fileName, cbzFileName);
            bookFile.setFileName(cbzFileName);
            bookAdditionalFileRepository.updateFileNameAndSubPath(bookFile.getId(), cbzFileName, bookFile.getFileSubPath());
        }
    }

    private boolean isLocalOrPrivateUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) return true;
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress();
        } catch (Exception e) {
            log.warn("Invalid thumbnail URL '{}': {}", url, e.getMessage());
            return true;
        }
    }
}
