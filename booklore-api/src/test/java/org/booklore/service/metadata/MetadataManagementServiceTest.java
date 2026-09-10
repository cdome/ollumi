package org.booklore.service.metadata;

import org.booklore.config.AppProperties;
import org.booklore.model.dto.FileMoveResult;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MergeMetadataType;
import org.booklore.repository.*;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookMetadataRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.metadata.writer.MetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.booklore.service.file.FileFingerprint;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataManagementServiceTest {

    @Mock private AppProperties appProperties;
    @Mock private org.booklore.repository.jooq.JooqBookMetadataRelationsRepository relationsRepository;
    @Mock private JooqBookMetadataRepository jooqBookMetadataRepository;
    @Mock private JooqBookRepository jooqBookRepository;
    @Mock private JooqBookEntityRepository jooqBookEntityRepository;
    @Mock private AppSettingService appSettingService;
    @Mock private MetadataWriterFactory metadataWriterFactory;
    @Mock private FileMoveService fileMoveService;

    @InjectMocks
    private MetadataManagementService service;

    @BeforeEach
    void setUp() {
        lenient().when(appProperties.isLocalStorage()).thenReturn(true);
        lenient().when(appSettingService.getAppSettings()).thenReturn(
                AppSettings.builder()
                        .metadataPersistenceSettings(MetadataPersistenceSettings.builder()
                                .moveFilesToLibraryPattern(false)
                                .build())
                        .build()
        );
    }

    /** Wraps a standalone BookMetadataEntity in a minimal BookEntity, mirroring what
     * JooqBookEntityRepository.findAllByIdsIncludingDeleted actually returns (metadata + its book
     * back-reference both populated). */
    private BookEntity bookWithMetadata(BookMetadataEntity metadata) {
        BookEntity book = BookEntity.builder().id(metadata.getBookId()).metadata(metadata).build();
        metadata.setBook(book);
        return book;
    }

    @Test
    void consolidateAuthors_mergesOldIntoTargetAndDeletesOld() {
        long bookId = 15L;
        when(relationsRepository.findBookIdsByAuthorNameIgnoreCase("Old Author")).thenReturn(List.of(bookId));
        when(relationsRepository.findAuthorNamesByBookId(bookId))
                .thenReturn(List.of("Old Author"), List.of("Target Author"));

        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).build();
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target Author"), List.of("Old Author"));

        verify(relationsRepository).renameOrCreateAuthor("Target Author");
        verify(relationsRepository).setAuthorsForBook(bookId, List.of("Target Author"));
        verify(relationsRepository).deleteAuthorByName("Old Author");
        assertThat(metadata.getAuthorNames()).isEqualTo(List.of("Target Author"));
        verify(jooqBookMetadataRepository).updateSearchText(eq(bookId), any());
    }

    @Test
    void consolidateCategories_mergesAndDeletesOld() {
        long bookId = 15L;
        when(relationsRepository.findBookIdsByCategoryNameIgnoreCase("Old Cat")).thenReturn(List.of(bookId));
        when(relationsRepository.findCategoryNamesByBookId(bookId)).thenReturn(Set.of("Old Cat"));

        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).build();
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.consolidateMetadata(MergeMetadataType.categories, List.of("Target Cat"), List.of("Old Cat"));

        verify(relationsRepository).renameOrCreateCategory("Target Cat");
        verify(relationsRepository).setCategoriesForBook(bookId, Set.of("Target Cat"));
        verify(relationsRepository).deleteCategoryByName("Old Cat");
    }

    @Test
    void consolidateMoods_mergesAndDeletesOld() {
        long bookId = 5L;
        when(relationsRepository.findBookIdsByMoodNameIgnoreCase("Old Mood")).thenReturn(List.of(bookId));
        when(relationsRepository.findMoodNamesByBookId(bookId)).thenReturn(Set.of("Old Mood"));

        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).build();
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.consolidateMetadata(MergeMetadataType.moods, List.of("Target Mood"), List.of("Old Mood"));

        verify(relationsRepository).renameOrCreateMood("Target Mood");
        verify(relationsRepository).setMoodsForBook(bookId, Set.of("Target Mood"));
        verify(relationsRepository).deleteMoodByName("Old Mood");
    }

    @Test
    void consolidateTags_mergesAndDeletesOld() {
        long bookId = 25L;
        when(relationsRepository.findBookIdsByTagNameIgnoreCase("Old Tag")).thenReturn(List.of(bookId));
        when(relationsRepository.findTagNamesByBookId(bookId)).thenReturn(Set.of("Old Tag"));

        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).build();
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.consolidateMetadata(MergeMetadataType.tags, List.of("Target Tag"), List.of("Old Tag"));

        verify(relationsRepository).renameOrCreateTag("Target Tag");
        verify(relationsRepository).setTagsForBook(bookId, Set.of("Target Tag"));
        verify(relationsRepository).deleteTagByName("Old Tag");
    }

    @Test
    void consolidateSeries_updatesSeriesNameOnAllBooks() {
        long bookId = 42L;
        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).seriesName("Old Series").build();
        when(jooqBookMetadataRepository.findBookIdsBySeriesNameIgnoreCase("Old Series")).thenReturn(List.of(bookId));
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));
        when(relationsRepository.findAuthorNamesByBookId(bookId)).thenReturn(List.of());

        service.consolidateMetadata(MergeMetadataType.series, List.of("New Series"), List.of("Old Series"));

        assertThat(metadata.getSeriesName()).isEqualTo("New Series");
        verify(jooqBookMetadataRepository).updateSeriesName(eq(bookId), eq("New Series"), any());
    }

    @Test
    void consolidateSeries_throwsWhenMultipleTargetValues() {
        assertThatThrownBy(() ->
                service.consolidateMetadata(MergeMetadataType.series, List.of("A", "B"), List.of("Old"))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one target value");
    }

    @Test
    void consolidatePublishers_updatesPublisherOnAllBooks() {
        long bookId = 43L;
        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).publisher("Old Pub").build();
        when(jooqBookMetadataRepository.findBookIdsByPublisherIgnoreCase("Old Pub")).thenReturn(List.of(bookId));
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.consolidateMetadata(MergeMetadataType.publishers, List.of("New Pub"), List.of("Old Pub"));

        assertThat(metadata.getPublisher()).isEqualTo("New Pub");
        verify(jooqBookMetadataRepository).updatePublisher(bookId, "New Pub");
    }

    @Test
    void consolidatePublishers_throwsWhenMultipleTargetValues() {
        assertThatThrownBy(() ->
                service.consolidateMetadata(MergeMetadataType.publishers, List.of("A", "B"), List.of("Old"))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consolidateLanguages_updatesLanguageOnAllBooks() {
        long bookId = 44L;
        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).language("fr").build();
        when(jooqBookMetadataRepository.findBookIdsByLanguageIgnoreCase("fr")).thenReturn(List.of(bookId));
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.consolidateMetadata(MergeMetadataType.languages, List.of("en"), List.of("fr"));

        assertThat(metadata.getLanguage()).isEqualTo("en");
        verify(jooqBookMetadataRepository).updateLanguage(bookId, "en");
    }

    @Test
    void consolidateLanguages_throwsWhenMultipleTargetValues() {
        assertThatThrownBy(() ->
                service.consolidateMetadata(MergeMetadataType.languages, List.of("en", "fr"), List.of("de"))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consolidateAuthors_writesMetadataToFileWhenWriterPresent() throws Exception {
        long bookId = 1L;
        when(relationsRepository.findBookIdsByAuthorNameIgnoreCase("Old")).thenReturn(List.of(bookId));
        when(relationsRepository.findAuthorNamesByBookId(bookId))
                .thenReturn(List.of("Old"), List.of("Target"));

        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-");
        java.nio.file.Path subDir = tempDir.resolve("sub");
        java.nio.file.Files.createDirectories(subDir);
        java.nio.file.Path tempFile = subDir.resolve("test.epub");
        java.nio.file.Files.createFile(tempFile);

        BookFileEntity bookFile = BookFileEntity.builder()
                .id(101L)
                .fileName("test.epub")
                .fileSubPath("sub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        BookEntity book = BookEntity.builder()
                .id(bookId)
                .bookFiles(new ArrayList<>(List.of(bookFile)))
                .libraryPath(libraryPath)
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .bookId(bookId)
                .book(book)
                .build();
        book.setMetadata(metadata);

        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(book));

        MetadataWriter writer = mock(MetadataWriter.class);
        when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.of(writer));

        try (MockedStatic<FileFingerprint> ffMock = mockStatic(FileFingerprint.class)) {
            ffMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("newhash");

            service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("Old"));
        }

        verify(writer).saveMetadataToFile(any(), eq(metadata), isNull(), isNull());
        verify(jooqBookRepository).updateBookFileHash(101L, "newhash");

        java.nio.file.Files.deleteIfExists(tempFile);
        java.nio.file.Files.deleteIfExists(subDir);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @Test
    void consolidateAuthors_movesFileWhenEnabled() throws Exception {
        when(appSettingService.getAppSettings()).thenReturn(
                AppSettings.builder()
                        .metadataPersistenceSettings(MetadataPersistenceSettings.builder()
                                .moveFilesToLibraryPattern(true)
                                .build())
                        .build()
        );

        long bookId = 1L;
        when(relationsRepository.findBookIdsByAuthorNameIgnoreCase("Old")).thenReturn(List.of(bookId));
        when(relationsRepository.findAuthorNamesByBookId(bookId))
                .thenReturn(List.of("Old"), List.of("Target"));

        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-move-");
        java.nio.file.Path subDir = tempDir.resolve("sub");
        java.nio.file.Files.createDirectories(subDir);
        java.nio.file.Path tempFile = subDir.resolve("test.epub");
        java.nio.file.Files.createFile(tempFile);

        BookFileEntity bookFile = BookFileEntity.builder()
                .id(102L)
                .fileName("test.epub")
                .fileSubPath("sub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        BookEntity book = BookEntity.builder()
                .id(bookId)
                .bookFiles(new ArrayList<>(List.of(bookFile)))
                .libraryPath(libraryPath)
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .bookId(bookId)
                .book(book)
                .build();
        book.setMetadata(metadata);

        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(book));
        when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.empty());
        when(fileMoveService.moveSingleFile(book)).thenReturn(
                FileMoveResult.builder().moved(true).newFileName("new.epub").newFileSubPath("new/sub").build()
        );

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("Old"));

        assertThat(bookFile.getFileName()).isEqualTo("new.epub");
        assertThat(bookFile.getFileSubPath()).isEqualTo("new/sub");
        verify(jooqBookRepository).updateFileNameAndSubPath(102L, "new.epub", "new/sub");

        java.nio.file.Files.deleteIfExists(tempFile);
        java.nio.file.Files.deleteIfExists(subDir);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @Test
    void deleteAuthors_removesFromBooksAndDeletesEntity() {
        long bookId = 1L;
        when(relationsRepository.findBookIdsByAuthorNameIgnoreCase("Author1")).thenReturn(List.of(bookId));
        when(relationsRepository.findAuthorNamesByBookId(bookId)).thenReturn(List.of("Author1"));

        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).build();
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.deleteMetadata(MergeMetadataType.authors, List.of("Author1"));

        verify(relationsRepository).setAuthorsForBook(bookId, List.of());
        verify(relationsRepository).deleteAuthorByName("Author1");
    }

    @Test
    void deleteCategories_removesFromBooksAndDeletesEntity() {
        long bookId = 17L;
        when(relationsRepository.findBookIdsByCategoryNameIgnoreCase("Cat1")).thenReturn(List.of(bookId));
        when(relationsRepository.findCategoryNamesByBookId(bookId)).thenReturn(Set.of("Cat1"));

        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).build();
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.deleteMetadata(MergeMetadataType.categories, List.of("Cat1"));

        verify(relationsRepository).setCategoriesForBook(bookId, Set.of());
        verify(relationsRepository).deleteCategoryByName("Cat1");
    }

    @Test
    void deleteMoods_removesFromBooksAndDeletesEntity() {
        long bookId = 7L;
        when(relationsRepository.findBookIdsByMoodNameIgnoreCase("Mood1")).thenReturn(List.of(bookId));
        when(relationsRepository.findMoodNamesByBookId(bookId)).thenReturn(Set.of("Mood1"));

        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).build();
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.deleteMetadata(MergeMetadataType.moods, List.of("Mood1"));

        verify(relationsRepository).setMoodsForBook(bookId, Set.of());
        verify(relationsRepository).deleteMoodByName("Mood1");
    }

    @Test
    void deleteTags_removesFromBooksAndDeletesEntity() {
        long bookId = 27L;
        when(relationsRepository.findBookIdsByTagNameIgnoreCase("Tag1")).thenReturn(List.of(bookId));
        when(relationsRepository.findTagNamesByBookId(bookId)).thenReturn(Set.of("Tag1"));

        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).build();
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.deleteMetadata(MergeMetadataType.tags, List.of("Tag1"));

        verify(relationsRepository).setTagsForBook(bookId, Set.of());
        verify(relationsRepository).deleteTagByName("Tag1");
    }

    @Test
    void deleteSeries_clearsSeriesFieldsFromBooks() {
        long bookId = 45L;
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .bookId(bookId)
                .seriesName("My Series")
                .seriesNumber(1.0f)
                .seriesTotal(3)
                .build();
        when(jooqBookMetadataRepository.findBookIdsBySeriesNameIgnoreCase("My Series")).thenReturn(List.of(bookId));
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));
        when(relationsRepository.findAuthorNamesByBookId(bookId)).thenReturn(List.of());

        service.deleteMetadata(MergeMetadataType.series, List.of("My Series"));

        assertThat(metadata.getSeriesName()).isNull();
        assertThat(metadata.getSeriesNumber()).isNull();
        assertThat(metadata.getSeriesTotal()).isNull();
        verify(jooqBookMetadataRepository).clearSeries(eq(bookId), any());
    }

    @Test
    void deletePublishers_clearsPublisherFromBooks() {
        long bookId = 46L;
        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).publisher("Old Pub").build();
        when(jooqBookMetadataRepository.findBookIdsByPublisherIgnoreCase("Old Pub")).thenReturn(List.of(bookId));
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.deleteMetadata(MergeMetadataType.publishers, List.of("Old Pub"));

        assertThat(metadata.getPublisher()).isNull();
        verify(jooqBookMetadataRepository).updatePublisher(bookId, null);
    }

    @Test
    void deleteLanguages_clearsLanguageFromBooks() {
        long bookId = 47L;
        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).language("fr").build();
        when(jooqBookMetadataRepository.findBookIdsByLanguageIgnoreCase("fr")).thenReturn(List.of(bookId));
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(bookWithMetadata(metadata)));

        service.deleteMetadata(MergeMetadataType.languages, List.of("fr"));

        assertThat(metadata.getLanguage()).isNull();
        verify(jooqBookMetadataRepository).updateLanguage(bookId, null);
    }

    @Test
    void deleteSeries_skipsWhenNoBooksFound() {
        when(jooqBookMetadataRepository.findBookIdsBySeriesNameIgnoreCase("Missing")).thenReturn(List.of());

        service.deleteMetadata(MergeMetadataType.series, List.of("Missing"));

        verify(jooqBookMetadataRepository, never()).clearSeries(anyLong(), any());
    }

    @Test
    void deletePublishers_skipsWhenNoBooksFound() {
        when(jooqBookMetadataRepository.findBookIdsByPublisherIgnoreCase("Missing")).thenReturn(List.of());

        service.deleteMetadata(MergeMetadataType.publishers, List.of("Missing"));

        verify(jooqBookMetadataRepository, never()).updatePublisher(anyLong(), any());
    }

    @Test
    void consolidateAuthors_skipsNonExistentMergeValues() {
        when(relationsRepository.findBookIdsByAuthorNameIgnoreCase("NonExistent")).thenReturn(List.of());

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("NonExistent"));

        verify(relationsRepository).renameOrCreateAuthor("Target");
        verify(jooqBookEntityRepository, never()).findAllByIdsIncludingDeleted(any());
        verify(relationsRepository, never()).deleteAuthorByName(any());
    }

    @Test
    void writeMetadataToFile_skipsWhenBookIsNull() {
        long bookId = 48L;
        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).book(null).build();
        BookEntity book = BookEntity.builder().id(bookId).metadata(metadata).build();
        // Deliberately NOT setting metadata.setBook(book) here, to exercise writeMetadataToFile's
        // defensive "book == null" guard even though the real hydrator always sets this back-reference.
        when(jooqBookMetadataRepository.findBookIdsBySeriesNameIgnoreCase("Old")).thenReturn(List.of(bookId));
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(book));
        when(relationsRepository.findAuthorNamesByBookId(bookId)).thenReturn(List.of());

        service.consolidateMetadata(MergeMetadataType.series, List.of("New"), List.of("Old"));

        verify(metadataWriterFactory, never()).getWriter(any());
        verify(jooqBookRepository, never()).updateBookFileHash(anyLong(), any());
        verify(jooqBookRepository, never()).updateFileNameAndSubPath(anyLong(), any(), any());
    }

    @Test
    void writeMetadataToFile_skipsFilelessPhysicalBookWithoutCrashing() {
        // Regression: writeMetadataToFile used to dereference book.getPrimaryBookFile() unconditionally,
        // NPEing for physical (fileless) books - masked under the old mocks because they never populated
        // metadata.getBook(), so this block was never actually exercised in tests.
        long bookId = 49L;
        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(bookId).seriesName("Old").build();
        BookEntity book = BookEntity.builder().id(bookId).metadata(metadata).isPhysical(true).build();
        metadata.setBook(book);

        when(jooqBookMetadataRepository.findBookIdsBySeriesNameIgnoreCase("Old")).thenReturn(List.of(bookId));
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(book));
        when(relationsRepository.findAuthorNamesByBookId(bookId)).thenReturn(List.of());

        service.consolidateMetadata(MergeMetadataType.series, List.of("New"), List.of("Old"));

        assertThat(metadata.getSeriesName()).isEqualTo("New");
        verify(metadataWriterFactory, never()).getWriter(any());
        verify(fileMoveService, never()).moveSingleFile(any());
    }

    @Test
    void writeMetadataToFile_skipsWriterWhenNoneAvailable() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-skip-");
        java.nio.file.Path subDir = tempDir.resolve("sub");
        java.nio.file.Files.createDirectories(subDir);
        java.nio.file.Path tempFile = subDir.resolve("test.epub");
        java.nio.file.Files.createFile(tempFile);

        BookFileEntity bookFile = BookFileEntity.builder()
                .fileName("test.epub")
                .fileSubPath("sub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        BookEntity book = BookEntity.builder()
                .id(1L)
                .bookFiles(new ArrayList<>(List.of(bookFile)))
                .libraryPath(libraryPath)
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .bookId(1L)
                .seriesName("Old")
                .book(book)
                .build();
        book.setMetadata(metadata);

        when(jooqBookMetadataRepository.findBookIdsBySeriesNameIgnoreCase("Old")).thenReturn(List.of(1L));
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(1L))).thenReturn(List.of(book));
        when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.empty());
        when(relationsRepository.findAuthorNamesByBookId(1L)).thenReturn(List.of());

        service.consolidateMetadata(MergeMetadataType.series, List.of("New"), List.of("Old"));

        verify(jooqBookRepository, never()).updateBookFileHash(anyLong(), any());
        verify(jooqBookRepository, never()).updateFileNameAndSubPath(anyLong(), any(), any());

        java.nio.file.Files.deleteIfExists(tempFile);
        java.nio.file.Files.deleteIfExists(subDir);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @Test
    void consolidateAuthors_fileMoveNotMovedDoesNotUpdateFileName() throws Exception {
        when(appSettingService.getAppSettings()).thenReturn(
                AppSettings.builder()
                        .metadataPersistenceSettings(MetadataPersistenceSettings.builder()
                                .moveFilesToLibraryPattern(true)
                                .build())
                        .build()
        );

        long bookId = 1L;
        when(relationsRepository.findBookIdsByAuthorNameIgnoreCase("Old")).thenReturn(List.of(bookId));
        when(relationsRepository.findAuthorNamesByBookId(bookId))
                .thenReturn(List.of("Old"), List.of("Target"));

        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-nomove-");
        java.nio.file.Path subDir = tempDir.resolve("sub");
        java.nio.file.Files.createDirectories(subDir);
        java.nio.file.Path tempFile = subDir.resolve("original.epub");
        java.nio.file.Files.createFile(tempFile);

        BookFileEntity bookFile = BookFileEntity.builder()
                .fileName("original.epub")
                .fileSubPath("sub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        BookEntity book = BookEntity.builder()
                .id(bookId)
                .bookFiles(new ArrayList<>(List.of(bookFile)))
                .libraryPath(libraryPath)
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .bookId(bookId)
                .book(book)
                .build();
        book.setMetadata(metadata);

        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(book));
        when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.empty());
        when(fileMoveService.moveSingleFile(book)).thenReturn(
                FileMoveResult.builder().moved(false).build()
        );

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("Old"));

        assertThat(bookFile.getFileName()).isEqualTo("original.epub");
        verify(jooqBookRepository, never()).updateFileNameAndSubPath(anyLong(), any(), any());

        java.nio.file.Files.deleteIfExists(tempFile);
        java.nio.file.Files.deleteIfExists(subDir);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @Test
    void consolidateAuthors_networkStorage_skipsFileWrite() {
        when(appProperties.isLocalStorage()).thenReturn(false);

        long bookId = 1L;
        when(relationsRepository.findBookIdsByAuthorNameIgnoreCase("Old")).thenReturn(List.of(bookId));
        when(relationsRepository.findAuthorNamesByBookId(bookId))
                .thenReturn(List.of("Old"), List.of("Target"));

        BookFileEntity bookFile = BookFileEntity.builder()
                .fileName("test.epub")
                .fileSubPath("sub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/fake/path");
        BookEntity book = BookEntity.builder()
                .id(bookId)
                .bookFiles(new ArrayList<>(List.of(bookFile)))
                .libraryPath(libraryPath)
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .bookId(bookId)
                .book(book)
                .build();
        book.setMetadata(metadata);

        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(bookId))).thenReturn(List.of(book));

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("Old"));

        verify(metadataWriterFactory, never()).getWriter(any());
        verify(relationsRepository).deleteAuthorByName("Old");
    }
}
