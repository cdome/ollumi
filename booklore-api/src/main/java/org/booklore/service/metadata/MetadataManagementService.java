package org.booklore.service.metadata;

import org.booklore.config.AppProperties;
import org.booklore.model.dto.FileMoveResult;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MergeMetadataType;
import org.booklore.repository.*;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookMetadataRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.metadata.writer.MetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.util.BookUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataManagementService {

    private final AppProperties appProperties;
    private final org.booklore.repository.jooq.JooqBookMetadataRelationsRepository relationsRepository;
    private final JooqBookMetadataRepository jooqBookMetadataRepository;
    private final JooqBookRepository jooqBookRepository;
    private final JooqBookEntityRepository jooqBookEntityRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final AppSettingService appSettingService;
    private final MetadataWriterFactory metadataWriterFactory;
    private final FileMoveService fileMoveService;


    @Transactional
    public void consolidateMetadata(MergeMetadataType metadataType, List<String> targetValues, List<String> valuesToMerge) {

        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean moveFile = settings.isMoveFilesToLibraryPattern();

        switch (metadataType) {
            case authors -> consolidateAuthors(targetValues, valuesToMerge, moveFile);
            case categories -> consolidateCategories(targetValues, valuesToMerge, moveFile);
            case moods -> consolidateMoods(targetValues, valuesToMerge, moveFile);
            case tags -> consolidateTags(targetValues, valuesToMerge, moveFile);
            case series -> consolidateSeries(targetValues, valuesToMerge, moveFile);
            case publishers -> consolidatePublishers(targetValues, valuesToMerge, moveFile);
            case languages -> consolidateLanguages(targetValues, valuesToMerge, moveFile);
        }
    }

    private List<BookMetadataEntity> findMetadataByIds(List<Long> bookIds) {
        return jooqBookEntityRepository.findAllByIdsIncludingDeleted(new HashSet<>(bookIds)).stream()
                .map(BookEntity::getMetadata)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void writeMetadataToFile(List<BookMetadataEntity> metadataList, boolean moveFile) {
        for (BookMetadataEntity metadata : metadataList) {
            if (metadata.getBook() != null) {
                BookEntity book = metadata.getBook();

                var primaryFile = book.getPrimaryBookFile();
                if (primaryFile == null) {
                    continue;
                }
                BookFileType bookType = primaryFile.getBookType();
                if (appProperties.isLocalStorage()) {
                    Optional<MetadataWriter> writerOpt = metadataWriterFactory.getWriter(bookType);
                    if (writerOpt.isPresent()) {
                        File file = book.getFullFilePath().toFile();
                        writerOpt.get().saveMetadataToFile(file, metadata, null, null);
                        String newHash = FileFingerprint.generateHash(book.getFullFilePath());
                        primaryFile.setCurrentHash(newHash);
                        jooqBookRepository.updateBookFileHash(primaryFile.getId(), newHash);
                    }
                }

                if (moveFile) {
                    FileMoveResult result = fileMoveService.moveSingleFile(book);
                    if (result.isMoved()) {
                        primaryFile.setFileName(result.getNewFileName());
                        primaryFile.setFileSubPath(result.getNewFileSubPath());
                        bookAdditionalFileRepository.updateFileNameAndSubPath(
                                primaryFile.getId(), result.getNewFileName(), result.getNewFileSubPath());
                    }
                }
            }
        }
    }

    private void consolidateAuthors(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        targetValues.forEach(relationsRepository::renameOrCreateAuthor);

        for (String oldAuthorName : valuesToMerge) {
            List<Long> affectedBookIds = relationsRepository.findBookIdsByAuthorNameIgnoreCase(oldAuthorName);
            if (affectedBookIds.isEmpty()) {
                continue;
            }

            for (Long bookId : affectedBookIds) {
                List<String> finalAuthors = new java.util.ArrayList<>(relationsRepository.findAuthorNamesByBookId(bookId));
                finalAuthors.remove(oldAuthorName);
                for (String targetName : targetValues) {
                    if (!finalAuthors.contains(targetName)) finalAuthors.add(targetName);
                }
                relationsRepository.setAuthorsForBook(bookId, finalAuthors);
            }

            List<BookMetadataEntity> booksWithOldAuthor = findMetadataByIds(affectedBookIds);
            for (BookMetadataEntity metadata : booksWithOldAuthor) {
                metadata.setAuthorNames(relationsRepository.findAuthorNamesByBookId(metadata.getBookId()));
                jooqBookMetadataRepository.updateSearchText(metadata.getBookId(), BookUtils.buildSearchText(metadata));
            }
            writeMetadataToFile(booksWithOldAuthor, moveFile);
            relationsRepository.deleteAuthorByName(oldAuthorName);
        }

        log.info("Consolidated {} authors into {}: {}", valuesToMerge.size(), targetValues, valuesToMerge);
    }

    private void consolidateCategories(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        targetValues.forEach(relationsRepository::renameOrCreateCategory);

        for (String oldCategoryName : valuesToMerge) {
            List<Long> affectedBookIds = relationsRepository.findBookIdsByCategoryNameIgnoreCase(oldCategoryName);
            if (affectedBookIds.isEmpty()) {
                continue;
            }

            for (Long bookId : affectedBookIds) {
                Set<String> finalCategories = new HashSet<>(relationsRepository.findCategoryNamesByBookId(bookId));
                finalCategories.remove(oldCategoryName);
                finalCategories.addAll(targetValues);
                relationsRepository.setCategoriesForBook(bookId, finalCategories);
            }

            List<BookMetadataEntity> booksWithOldCategory = findMetadataByIds(affectedBookIds);
            writeMetadataToFile(booksWithOldCategory, moveFile);
            relationsRepository.deleteCategoryByName(oldCategoryName);
        }

        log.info("Consolidated {} categories into {}: {}", valuesToMerge.size(), targetValues, valuesToMerge);
    }

    private void consolidateMoods(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        targetValues.forEach(relationsRepository::renameOrCreateMood);

        for (String oldMoodName : valuesToMerge) {
            List<Long> affectedBookIds = relationsRepository.findBookIdsByMoodNameIgnoreCase(oldMoodName);
            if (affectedBookIds.isEmpty()) {
                continue;
            }

            for (Long bookId : affectedBookIds) {
                Set<String> finalMoods = new HashSet<>(relationsRepository.findMoodNamesByBookId(bookId));
                finalMoods.remove(oldMoodName);
                finalMoods.addAll(targetValues);
                relationsRepository.setMoodsForBook(bookId, finalMoods);
            }

            List<BookMetadataEntity> booksWithOldMood = findMetadataByIds(affectedBookIds);
            writeMetadataToFile(booksWithOldMood, moveFile);
            relationsRepository.deleteMoodByName(oldMoodName);
        }

        log.info("Consolidated {} moods into {}: {}", valuesToMerge.size(), targetValues, valuesToMerge);
    }

    private void consolidateTags(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        targetValues.forEach(relationsRepository::renameOrCreateTag);

        for (String oldTagName : valuesToMerge) {
            List<Long> affectedBookIds = relationsRepository.findBookIdsByTagNameIgnoreCase(oldTagName);
            if (affectedBookIds.isEmpty()) {
                continue;
            }

            for (Long bookId : affectedBookIds) {
                Set<String> finalTags = new HashSet<>(relationsRepository.findTagNamesByBookId(bookId));
                finalTags.remove(oldTagName);
                finalTags.addAll(targetValues);
                relationsRepository.setTagsForBook(bookId, finalTags);
            }

            List<BookMetadataEntity> booksWithOldTag = findMetadataByIds(affectedBookIds);
            writeMetadataToFile(booksWithOldTag, moveFile);
            relationsRepository.deleteTagByName(oldTagName);
        }

        log.info("Consolidated {} tags into {}: {}", valuesToMerge.size(), targetValues, valuesToMerge);
    }

    private void consolidateSeries(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        if (targetValues.size() != 1) {
            throw new IllegalArgumentException("Series merge requires exactly one target value");
        }
        String targetSeriesName = trimOrNull(targetValues.getFirst());

        for (String oldSeriesName : valuesToMerge) {
            List<BookMetadataEntity> booksWithOldSeries = findMetadataByIds(jooqBookMetadataRepository.findBookIdsBySeriesNameIgnoreCase(oldSeriesName));

            for (BookMetadataEntity metadata : booksWithOldSeries) {
                metadata.setSeriesName(targetSeriesName);
                metadata.setAuthorNames(relationsRepository.findAuthorNamesByBookId(metadata.getBookId()));
                jooqBookMetadataRepository.updateSeriesName(metadata.getBookId(), targetSeriesName, BookUtils.buildSearchText(metadata));
            }

            writeMetadataToFile(booksWithOldSeries, moveFile);
        }

        log.info("Consolidated {} series into '{}': {}", valuesToMerge.size(), targetSeriesName, valuesToMerge);
    }

    private void consolidatePublishers(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        if (targetValues.size() != 1) {
            throw new IllegalArgumentException("Publisher merge requires exactly one target value");
        }
        String targetPublisher = trimOrNull(targetValues.getFirst());

        for (String oldPublisher : valuesToMerge) {
            List<BookMetadataEntity> booksWithOldPublisher = findMetadataByIds(jooqBookMetadataRepository.findBookIdsByPublisherIgnoreCase(oldPublisher));

            for (BookMetadataEntity metadata : booksWithOldPublisher) {
                metadata.setPublisher(targetPublisher);
                jooqBookMetadataRepository.updatePublisher(metadata.getBookId(), targetPublisher);
            }

            writeMetadataToFile(booksWithOldPublisher, moveFile);
        }

        log.info("Consolidated {} publishers into '{}': {}", valuesToMerge.size(), targetPublisher, valuesToMerge);
    }

    private void consolidateLanguages(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        if (targetValues.size() != 1) {
            throw new IllegalArgumentException("Language merge requires exactly one target value");
        }
        String targetLanguage = trimOrNull(targetValues.getFirst());

        for (String oldLanguage : valuesToMerge) {
            List<BookMetadataEntity> booksWithOldLanguage = findMetadataByIds(jooqBookMetadataRepository.findBookIdsByLanguageIgnoreCase(oldLanguage));

            for (BookMetadataEntity metadata : booksWithOldLanguage) {
                metadata.setLanguage(targetLanguage);
                jooqBookMetadataRepository.updateLanguage(metadata.getBookId(), targetLanguage);
            }

            writeMetadataToFile(booksWithOldLanguage, moveFile);
        }

        log.info("Consolidated {} languages into '{}': {}", valuesToMerge.size(), targetLanguage, valuesToMerge);
    }

    @Transactional
    public void deleteMetadata(MergeMetadataType metadataType, List<String> valuesToDelete) {
        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean moveFile = settings.isMoveFilesToLibraryPattern();

        switch (metadataType) {
            case authors -> deleteAuthors(valuesToDelete, moveFile);
            case categories -> deleteCategories(valuesToDelete, moveFile);
            case moods -> deleteMoods(valuesToDelete, moveFile);
            case tags -> deleteTags(valuesToDelete, moveFile);
            case series -> deleteSeries(valuesToDelete, moveFile);
            case publishers -> deletePublishers(valuesToDelete, moveFile);
            case languages -> deleteLanguages(valuesToDelete, moveFile);
        }
    }

    private void deleteAuthors(List<String> valuesToDelete, boolean moveFile) {
        for (String authorName : valuesToDelete) {
            List<Long> affectedBookIds = relationsRepository.findBookIdsByAuthorNameIgnoreCase(authorName);
            if (affectedBookIds.isEmpty()) {
                continue;
            }

            for (Long bookId : affectedBookIds) {
                List<String> finalAuthors = new java.util.ArrayList<>(relationsRepository.findAuthorNamesByBookId(bookId));
                finalAuthors.remove(authorName);
                relationsRepository.setAuthorsForBook(bookId, finalAuthors);
            }

            List<BookMetadataEntity> booksWithAuthor = findMetadataByIds(affectedBookIds);
            writeMetadataToFile(booksWithAuthor, moveFile);
            relationsRepository.deleteAuthorByName(authorName);
        }

        log.info("Deleted {} authors: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deleteCategories(List<String> valuesToDelete, boolean moveFile) {
        for (String categoryName : valuesToDelete) {
            List<Long> affectedBookIds = relationsRepository.findBookIdsByCategoryNameIgnoreCase(categoryName);
            if (affectedBookIds.isEmpty()) {
                continue;
            }

            for (Long bookId : affectedBookIds) {
                Set<String> finalCategories = new HashSet<>(relationsRepository.findCategoryNamesByBookId(bookId));
                finalCategories.remove(categoryName);
                relationsRepository.setCategoriesForBook(bookId, finalCategories);
            }

            List<BookMetadataEntity> booksWithCategory = findMetadataByIds(affectedBookIds);
            writeMetadataToFile(booksWithCategory, moveFile);
            relationsRepository.deleteCategoryByName(categoryName);
        }

        log.info("Deleted {} categories: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deleteMoods(List<String> valuesToDelete, boolean moveFile) {
        for (String moodName : valuesToDelete) {
            List<Long> affectedBookIds = relationsRepository.findBookIdsByMoodNameIgnoreCase(moodName);
            if (affectedBookIds.isEmpty()) {
                continue;
            }

            for (Long bookId : affectedBookIds) {
                Set<String> finalMoods = new HashSet<>(relationsRepository.findMoodNamesByBookId(bookId));
                finalMoods.remove(moodName);
                relationsRepository.setMoodsForBook(bookId, finalMoods);
            }

            List<BookMetadataEntity> booksWithMood = findMetadataByIds(affectedBookIds);
            writeMetadataToFile(booksWithMood, moveFile);
            relationsRepository.deleteMoodByName(moodName);
        }

        log.info("Deleted {} moods: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deleteTags(List<String> valuesToDelete, boolean moveFile) {
        for (String tagName : valuesToDelete) {
            List<Long> affectedBookIds = relationsRepository.findBookIdsByTagNameIgnoreCase(tagName);
            if (affectedBookIds.isEmpty()) {
                continue;
            }

            for (Long bookId : affectedBookIds) {
                Set<String> finalTags = new HashSet<>(relationsRepository.findTagNamesByBookId(bookId));
                finalTags.remove(tagName);
                relationsRepository.setTagsForBook(bookId, finalTags);
            }

            List<BookMetadataEntity> booksWithTag = findMetadataByIds(affectedBookIds);
            writeMetadataToFile(booksWithTag, moveFile);
            relationsRepository.deleteTagByName(tagName);
        }

        log.info("Deleted {} tags: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deleteSeries(List<String> valuesToDelete, boolean moveFile) {
        for (String seriesName : valuesToDelete) {
            List<BookMetadataEntity> booksWithSeries = findMetadataByIds(jooqBookMetadataRepository.findBookIdsBySeriesNameIgnoreCase(seriesName));

            for (BookMetadataEntity metadata : booksWithSeries) {
                metadata.setSeriesName(null);
                metadata.setSeriesNumber(null);
                metadata.setSeriesTotal(null);
                metadata.setAuthorNames(relationsRepository.findAuthorNamesByBookId(metadata.getBookId()));
                jooqBookMetadataRepository.clearSeries(metadata.getBookId(), BookUtils.buildSearchText(metadata));
            }

            if (!booksWithSeries.isEmpty()) {
                writeMetadataToFile(booksWithSeries, moveFile);
            }
        }

        log.info("Deleted {} series: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deletePublishers(List<String> valuesToDelete, boolean moveFile) {
        for (String publisher : valuesToDelete) {
            List<BookMetadataEntity> booksWithPublisher = findMetadataByIds(jooqBookMetadataRepository.findBookIdsByPublisherIgnoreCase(publisher));

            for (BookMetadataEntity metadata : booksWithPublisher) {
                metadata.setPublisher(null);
                jooqBookMetadataRepository.updatePublisher(metadata.getBookId(), null);
            }

            if (!booksWithPublisher.isEmpty()) {
                writeMetadataToFile(booksWithPublisher, moveFile);
            }
        }

        log.info("Deleted {} publishers: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deleteLanguages(List<String> valuesToDelete, boolean moveFile) {
        for (String language : valuesToDelete) {
            List<BookMetadataEntity> booksWithLanguage = findMetadataByIds(jooqBookMetadataRepository.findBookIdsByLanguageIgnoreCase(language));

            for (BookMetadataEntity metadata : booksWithLanguage) {
                metadata.setLanguage(null);
                jooqBookMetadataRepository.updateLanguage(metadata.getBookId(), null);
            }

            if (!booksWithLanguage.isEmpty()) {
                writeMetadataToFile(booksWithLanguage, moveFile);
            }
        }

        log.info("Deleted {} languages: {}", valuesToDelete.size(), valuesToDelete);
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
