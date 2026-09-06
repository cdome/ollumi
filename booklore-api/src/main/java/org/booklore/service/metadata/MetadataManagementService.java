package org.booklore.service.metadata;

import org.booklore.config.AppProperties;
import org.booklore.model.dto.FileMoveResult;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MergeMetadataType;
import org.booklore.repository.*;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.metadata.writer.MetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
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
    private final AuthorRepository authorRepository;
    private final org.booklore.repository.jooq.JooqBookMetadataRelationsRepository relationsRepository;
    private final BookMetadataRepository bookMetadataRepository;
    private final AppSettingService appSettingService;
    private final MetadataWriterFactory metadataWriterFactory;
    private final FileMoveService fileMoveService;
    private final BookRepository bookRepository;


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

    private void writeMetadataToFile(List<BookMetadataEntity> metadataList, boolean moveFile) {
        for (BookMetadataEntity metadata : metadataList) {
            if (metadata.getBook() != null) {
                BookEntity book = metadata.getBook();
                boolean bookModified = false;

                var primaryFile = book.getPrimaryBookFile();
                BookFileType bookType = primaryFile.getBookType();
                if (appProperties.isLocalStorage()) {
                    Optional<MetadataWriter> writerOpt = metadataWriterFactory.getWriter(bookType);
                    if (writerOpt.isPresent()) {
                        File file = book.getFullFilePath().toFile();
                        writerOpt.get().saveMetadataToFile(file, metadata, null, null);
                        String newHash = FileFingerprint.generateHash(book.getFullFilePath());
                        primaryFile.setCurrentHash(newHash);
                        bookModified = true;
                    }
                }

                if (moveFile) {
                    FileMoveResult result = fileMoveService.moveSingleFile(book);
                    if (result.isMoved()) {
                        primaryFile.setFileName(result.getNewFileName());
                        primaryFile.setFileSubPath(result.getNewFileSubPath());
                        bookModified = true;
                    }
                }

                if (bookModified) {
                    bookRepository.saveAndFlush(book);
                }
            }
        }
    }

    private void consolidateAuthors(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        List<AuthorEntity> targetAuthors = targetValues.stream()
                .map(name -> authorRepository.findByNameIgnoreCase(name)
                        .map(existing -> {
                            existing.setName(name);
                            return authorRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            AuthorEntity author = new AuthorEntity();
                            author.setName(name);
                            return authorRepository.save(author);
                        }))
                .toList();

        List<AuthorEntity> authorsToMerge = valuesToMerge.stream()
                .map(authorRepository::findByNameIgnoreCase)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        for (AuthorEntity oldAuthor : authorsToMerge) {
            List<BookMetadataEntity> booksWithOldAuthor = bookMetadataRepository.findAllByAuthorsContaining(oldAuthor);

            for (BookMetadataEntity metadata : booksWithOldAuthor) {
                metadata.getAuthors().remove(oldAuthor);
                metadata.getAuthors().addAll(targetAuthors);
                metadata.updateSearchText();
            }

            bookMetadataRepository.saveAll(booksWithOldAuthor);
            bookMetadataRepository.flush();
            writeMetadataToFile(booksWithOldAuthor, moveFile);
            authorRepository.delete(oldAuthor);
        }

        log.info("Consolidated {} authors into {}: {}", authorsToMerge.size(), targetValues, valuesToMerge);
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

            List<BookMetadataEntity> booksWithOldCategory = bookMetadataRepository.findAllById(affectedBookIds);
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

            List<BookMetadataEntity> booksWithOldMood = bookMetadataRepository.findAllById(affectedBookIds);
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

            List<BookMetadataEntity> booksWithOldTag = bookMetadataRepository.findAllById(affectedBookIds);
            writeMetadataToFile(booksWithOldTag, moveFile);
            relationsRepository.deleteTagByName(oldTagName);
        }

        log.info("Consolidated {} tags into {}: {}", valuesToMerge.size(), targetValues, valuesToMerge);
    }

    private void consolidateSeries(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        if (targetValues.size() != 1) {
            throw new IllegalArgumentException("Series merge requires exactly one target value");
        }
        String targetSeriesName = targetValues.getFirst();

        for (String oldSeriesName : valuesToMerge) {
            List<BookMetadataEntity> booksWithOldSeries = bookMetadataRepository.findAllBySeriesNameIgnoreCase(oldSeriesName);

            for (BookMetadataEntity metadata : booksWithOldSeries) {
                metadata.setSeriesName(targetSeriesName);
            }

            bookMetadataRepository.saveAll(booksWithOldSeries);
            writeMetadataToFile(booksWithOldSeries, moveFile);
        }

        log.info("Consolidated {} series into '{}': {}", valuesToMerge.size(), targetSeriesName, valuesToMerge);
    }

    private void consolidatePublishers(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        if (targetValues.size() != 1) {
            throw new IllegalArgumentException("Publisher merge requires exactly one target value");
        }
        String targetPublisher = targetValues.getFirst();

        for (String oldPublisher : valuesToMerge) {
            List<BookMetadataEntity> booksWithOldPublisher = bookMetadataRepository.findAllByPublisherIgnoreCase(oldPublisher);

            for (BookMetadataEntity metadata : booksWithOldPublisher) {
                metadata.setPublisher(targetPublisher);
            }

            bookMetadataRepository.saveAll(booksWithOldPublisher);
            writeMetadataToFile(booksWithOldPublisher, moveFile);
        }

        log.info("Consolidated {} publishers into '{}': {}", valuesToMerge.size(), targetPublisher, valuesToMerge);
    }

    private void consolidateLanguages(List<String> targetValues, List<String> valuesToMerge, boolean moveFile) {
        if (targetValues.size() != 1) {
            throw new IllegalArgumentException("Language merge requires exactly one target value");
        }
        String targetLanguage = targetValues.getFirst();

        for (String oldLanguage : valuesToMerge) {
            List<BookMetadataEntity> booksWithOldLanguage = bookMetadataRepository.findAllByLanguageIgnoreCase(oldLanguage);

            for (BookMetadataEntity metadata : booksWithOldLanguage) {
                metadata.setLanguage(targetLanguage);
            }

            bookMetadataRepository.saveAll(booksWithOldLanguage);
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
        List<AuthorEntity> authorsToDelete = valuesToDelete.stream()
                .map(authorRepository::findByName)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        for (AuthorEntity author : authorsToDelete) {
            List<BookMetadataEntity> booksWithAuthor = bookMetadataRepository.findAllByAuthorsContaining(author);

            for (BookMetadataEntity metadata : booksWithAuthor) {
                metadata.getAuthors().remove(author);
            }

            bookMetadataRepository.saveAll(booksWithAuthor);
            bookMetadataRepository.flush();
            writeMetadataToFile(booksWithAuthor, moveFile);
            authorRepository.delete(author);
        }

        log.info("Deleted {} authors: {}", authorsToDelete.size(), valuesToDelete);
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

            List<BookMetadataEntity> booksWithCategory = bookMetadataRepository.findAllById(affectedBookIds);
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

            List<BookMetadataEntity> booksWithMood = bookMetadataRepository.findAllById(affectedBookIds);
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

            List<BookMetadataEntity> booksWithTag = bookMetadataRepository.findAllById(affectedBookIds);
            writeMetadataToFile(booksWithTag, moveFile);
            relationsRepository.deleteTagByName(tagName);
        }

        log.info("Deleted {} tags: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deleteSeries(List<String> valuesToDelete, boolean moveFile) {
        for (String seriesName : valuesToDelete) {
            List<BookMetadataEntity> booksWithSeries = bookMetadataRepository.findAllBySeriesNameIgnoreCase(seriesName);

            for (BookMetadataEntity metadata : booksWithSeries) {
                metadata.setSeriesName(null);
                metadata.setSeriesNumber(null);
                metadata.setSeriesTotal(null);
            }

            if (!booksWithSeries.isEmpty()) {
                bookMetadataRepository.saveAll(booksWithSeries);
                writeMetadataToFile(booksWithSeries, moveFile);
            }
        }

        log.info("Deleted {} series: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deletePublishers(List<String> valuesToDelete, boolean moveFile) {
        for (String publisher : valuesToDelete) {
            List<BookMetadataEntity> booksWithPublisher = bookMetadataRepository.findAllByPublisherIgnoreCase(publisher);

            for (BookMetadataEntity metadata : booksWithPublisher) {
                metadata.setPublisher(null);
            }

            if (!booksWithPublisher.isEmpty()) {
                bookMetadataRepository.saveAll(booksWithPublisher);
                writeMetadataToFile(booksWithPublisher, moveFile);
            }
        }

        log.info("Deleted {} publishers: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deleteLanguages(List<String> valuesToDelete, boolean moveFile) {
        for (String language : valuesToDelete) {
            List<BookMetadataEntity> booksWithLanguage = bookMetadataRepository.findAllByLanguageIgnoreCase(language);

            for (BookMetadataEntity metadata : booksWithLanguage) {
                metadata.setLanguage(null);
            }

            if (!booksWithLanguage.isEmpty()) {
                bookMetadataRepository.saveAll(booksWithLanguage);
                writeMetadataToFile(booksWithLanguage, moveFile);
            }
        }

        log.info("Deleted {} languages: {}", valuesToDelete.size(), valuesToDelete);
    }
}
