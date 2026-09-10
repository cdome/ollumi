package org.booklore.service.metadata;

import org.booklore.config.AppProperties;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.dto.FileMoveResult;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.*;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.util.FileService;
import org.booklore.util.MetadataChangeDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookMetadataUpdaterTest {

    @Mock private AppProperties appProperties;
    @Mock private org.booklore.repository.jooq.JooqBookMetadataRelationsRepository relationsRepository;
    @Mock private org.booklore.repository.jooq.JooqBookRepository jooqBookRepository;
    @Mock private org.booklore.repository.jooq.JooqBookMetadataRepository jooqBookMetadataRepository;
    @Mock private org.booklore.repository.jooq.JooqComicMetadataRepository comicRelationsRepository;
    @Mock private FileService fileService;
    @Mock private MetadataMatchService metadataMatchService;
    @Mock private AppSettingService appSettingService;
    @Mock private MetadataWriterFactory metadataWriterFactory;
    @Mock private BookReviewUpdateService bookReviewUpdateService;
    @Mock private FileMoveService fileMoveService;
    @Mock private SidecarMetadataWriter sidecarMetadataWriter;

    @InjectMocks
    private BookMetadataUpdater updater;

    private BookEntity bookEntity;
    private BookMetadataEntity metadataEntity;

    @BeforeEach
    void setUp() {
        lenient().when(appProperties.isLocalStorage()).thenReturn(true);
        metadataEntity = BookMetadataEntity.builder()
                .bookId(1L)
                .title("Original Title")
                .authorNames(new ArrayList<>())
                .build();

        bookEntity = BookEntity.builder()
                .id(1L)
                .metadata(metadataEntity)
                .bookFiles(new ArrayList<>())
                .build();
        metadataEntity.setBook(bookEntity);
    }

    private MetadataUpdateContext buildContext(BookMetadata newMeta, MetadataReplaceMode mode) {
        return MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).build())
                .replaceMode(mode)
                .build();
    }

    private void mockSettingsAndChangeDetector(MockedStatic<MetadataChangeDetector> mcd, boolean hasDiff, boolean hasValueChanges) {
        MetadataPersistenceSettings settings = MetadataPersistenceSettings.builder()
                .saveToOriginalFile(MetadataPersistenceSettings.SaveToOriginalFile.builder().build())
                .build();
        when(appSettingService.getAppSettings()).thenReturn(
                AppSettings.builder().metadataPersistenceSettings(settings).build());
        when(sidecarMetadataWriter.isWriteOnUpdateEnabled()).thenReturn(false);

        mcd.when(() -> MetadataChangeDetector.isDifferent(any(), any(), any())).thenReturn(hasDiff);
        mcd.when(() -> MetadataChangeDetector.hasValueChanges(any(), any(), any())).thenReturn(hasValueChanges);
        mcd.when(() -> MetadataChangeDetector.hasLockChanges(any(), any())).thenReturn(false);
        mcd.when(() -> MetadataChangeDetector.hasValueChangesForFileWrite(any(), any(), any())).thenReturn(false);
    }

    @Test
    void setBookMetadata_skipsWhenMetadataIsNull() {
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(null).build())
                .build();

        updater.setBookMetadata(context);

        verify(jooqBookMetadataRepository, never()).updateFullRow(any());
    }

    @Test
    void setBookMetadata_skipsWhenNoChangesDetected() {
        BookMetadata newMeta = BookMetadata.builder().title("Original Title").build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mcd.when(() -> MetadataChangeDetector.isDifferent(any(), any(), any())).thenReturn(false);
            mcd.when(() -> MetadataChangeDetector.hasValueChanges(any(), any(), any())).thenReturn(false);

            updater.setBookMetadata(context);

            verify(jooqBookMetadataRepository, never()).updateFullRow(any());
        }
    }

    @Test
    void setBookMetadata_skipsWhenAllFieldsLockedAndHasValueChanges() {
        metadataEntity.applyLockToAllFields(true);
        BookMetadata newMeta = BookMetadata.builder().title("New Title").build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mcd.when(() -> MetadataChangeDetector.isDifferent(any(), any(), any())).thenReturn(true);
            mcd.when(() -> MetadataChangeDetector.hasValueChanges(any(), any(), any())).thenReturn(true);
            mcd.when(() -> MetadataChangeDetector.hasLockChanges(any(), any())).thenReturn(false);

            updater.setBookMetadata(context);

            verify(jooqBookMetadataRepository, never()).updateFullRow(any());
        }
    }

    @Test
    void setBookMetadata_proceedsWhenAllLockedButHasLockChanges() {
        metadataEntity.applyLockToAllFields(true);
        BookMetadata newMeta = BookMetadata.builder().title("New Title").titleLocked(false).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);
            mcd.when(() -> MetadataChangeDetector.hasLockChanges(any(), any())).thenReturn(true);

            updater.setBookMetadata(context);

            verify(jooqBookMetadataRepository).updateFullRow(metadataEntity);
        }
    }

    @Test
    void setBookMetadata_replaceAll_updatesTitle() {
        BookMetadata newMeta = BookMetadata.builder().title("New Title").build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getTitle()).isEqualTo("New Title");
        }
    }

    @Test
    void setBookMetadata_replaceAll_setsNullWhenNewValueNull() {
        metadataEntity.setPublisher("Old Publisher");
        BookMetadata newMeta = BookMetadata.builder().title("T").publisher(null).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getPublisher()).isNull();
        }
    }

    @Test
    void setBookMetadata_replaceMissing_doesNotOverwriteExisting() {
        metadataEntity.setPublisher("Existing Publisher");
        BookMetadata newMeta = BookMetadata.builder().title("T").publisher("New Publisher").build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getPublisher()).isEqualTo("Existing Publisher");
        }
    }

    @Test
    void setBookMetadata_replaceMissing_fillsWhenExistingIsNull() {
        metadataEntity.setPublisher(null);
        BookMetadata newMeta = BookMetadata.builder().title("T").publisher("New Publisher").build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getPublisher()).isEqualTo("New Publisher");
        }
    }

    @Test
    void setBookMetadata_replaceMissing_fillsWhenExistingIsBlank() {
        metadataEntity.setPublisher("  ");
        BookMetadata newMeta = BookMetadata.builder().title("T").publisher("New Publisher").build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getPublisher()).isEqualTo("New Publisher");
        }
    }

    @Test
    void setBookMetadata_replaceWhenProvided_setsWhenNewValueNotBlank() {
        metadataEntity.setPublisher("Existing");
        BookMetadata newMeta = BookMetadata.builder().title("T").publisher("New").build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_WHEN_PROVIDED);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getPublisher()).isEqualTo("New");
        }
    }

    @Test
    void setBookMetadata_replaceWhenProvided_skipsWhenNewValueNull() {
        metadataEntity.setPublisher("Existing");
        BookMetadata newMeta = BookMetadata.builder().title("T").publisher(null).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_WHEN_PROVIDED);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getPublisher()).isEqualTo("Existing");
        }
    }

    @Test
    void setBookMetadata_lockedField_notUpdated() {
        metadataEntity.setTitleLocked(true);
        metadataEntity.setTitle("Locked Title");
        BookMetadata newMeta = BookMetadata.builder().title("New Title").build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getTitle()).isEqualTo("Locked Title");
        }
    }

    @Test
    void setBookMetadata_clearFlag_setsFieldToNull() {
        metadataEntity.setPublisher("Some Publisher");
        BookMetadata newMeta = BookMetadata.builder().title("T").publisher("Ignored").build();
        MetadataClearFlags clearFlags = new MetadataClearFlags();
        clearFlags.setPublisher(true);

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).clearFlags(clearFlags).build())
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getPublisher()).isNull();
        }
    }

    @Test
    void setBookMetadata_clearFlag_respectedEvenWhenLocked() {
        metadataEntity.setPublisherLocked(true);
        metadataEntity.setPublisher("Locked Publisher");
        BookMetadata newMeta = BookMetadata.builder().title("T").build();
        MetadataClearFlags clearFlags = new MetadataClearFlags();
        clearFlags.setPublisher(true);

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).clearFlags(clearFlags).build())
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getPublisher()).isEqualTo("Locked Publisher");
        }
    }

    @Test
    void setBookMetadata_authorsReplaceAll_replacesExisting() {
        when(relationsRepository.findAuthorNamesByBookId(1L)).thenReturn(List.of("Old Author"));

        BookMetadata newMeta = BookMetadata.builder().title("T").authors(List.of("New Author")).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository).setAuthorsForBook(1L, List.of("New Author"));
            assertThat(metadataEntity.getAuthorNames()).containsExactly("New Author");
        }
    }

    @Test
    void setBookMetadata_authorsReplaceMissing_skipsWhenExistingAuthorsPresent() {
        when(relationsRepository.findAuthorNamesByBookId(1L)).thenReturn(List.of("Existing"));

        BookMetadata newMeta = BookMetadata.builder().title("T").authors(List.of("New Author")).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository, never()).setAuthorsForBook(eq(1L), any());
            assertThat(metadataEntity.getAuthorNames()).containsExactly("Existing");
        }
    }

    @Test
    void setBookMetadata_authorsReplaceMissing_addsWhenEmpty() {
        when(relationsRepository.findAuthorNamesByBookId(1L)).thenReturn(List.of());

        BookMetadata newMeta = BookMetadata.builder().title("T").authors(List.of("New")).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository).setAuthorsForBook(1L, List.of("New"));
            assertThat(metadataEntity.getAuthorNames()).containsExactly("New");
        }
    }

    @Test
    void setBookMetadata_authorsLocked_notUpdated() {
        metadataEntity.setAuthorsLocked(true);
        when(relationsRepository.findAuthorNamesByBookId(1L)).thenReturn(List.of("Locked"));

        BookMetadata newMeta = BookMetadata.builder().title("T").authors(List.of("New Author")).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository, never()).setAuthorsForBook(eq(1L), any());
            assertThat(metadataEntity.getAuthorNames()).containsExactly("Locked");
        }
    }

    @Test
    void setBookMetadata_clearAuthors_clearsSet() {
        when(relationsRepository.findAuthorNamesByBookId(1L)).thenReturn(List.of("Author"));

        BookMetadata newMeta = BookMetadata.builder().title("T").build();
        MetadataClearFlags clearFlags = new MetadataClearFlags();
        clearFlags.setAuthors(true);

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).clearFlags(clearFlags).build())
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository).clearAuthorsForBook(1L);
            assertThat(metadataEntity.getAuthorNames()).isEmpty();
        }
    }

    @Test
    void setBookMetadata_categoriesReplaceAll_noMerge_replacesExisting() {
        when(relationsRepository.findCategoryNamesByBookId(1L)).thenReturn(Set.of("Old Cat"));

        BookMetadata newMeta = BookMetadata.builder().title("T").categories(Set.of("New Cat")).build();
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).build())
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .mergeCategories(false)
                .build();

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository).setCategoriesForBook(1L, Set.of("New Cat"));
        }
    }

    @Test
    void setBookMetadata_categoriesReplaceAll_merge_keepsExistingAndAddsNew() {
        when(relationsRepository.findCategoryNamesByBookId(1L)).thenReturn(Set.of("Old Cat"));

        BookMetadata newMeta = BookMetadata.builder().title("T").categories(Set.of("New Cat")).build();
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).build())
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .mergeCategories(true)
                .build();

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository).setCategoriesForBook(1L, Set.of("Old Cat", "New Cat"));
        }
    }

    @Test
    void setBookMetadata_moodsReplaceAll_noMerge_replacesExisting() {
        when(relationsRepository.findMoodNamesByBookId(1L)).thenReturn(Set.of("Old Mood"));

        BookMetadata newMeta = BookMetadata.builder().title("T").moods(Set.of("New Mood")).build();
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).build())
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .mergeMoods(false)
                .build();

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository).setMoodsForBook(1L, Set.of("New Mood"));
        }
    }

    @Test
    void setBookMetadata_tagsReplaceAll_merge_keepsExisting() {
        when(relationsRepository.findTagNamesByBookId(1L)).thenReturn(Set.of("Old Tag"));

        BookMetadata newMeta = BookMetadata.builder().title("T").tags(Set.of("New Tag")).build();
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).build())
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .mergeTags(true)
                .build();

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository).setTagsForBook(1L, Set.of("Old Tag", "New Tag"));
        }
    }

    @Test
    void setBookMetadata_authorsReplaceAll_emptyNewAuthors_clearsExisting() {
        when(relationsRepository.findAuthorNamesByBookId(1L)).thenReturn(List.of("Author"));

        BookMetadata newMeta = BookMetadata.builder().title("T").authors(List.of()).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository).clearAuthorsForBook(1L);
            assertThat(metadataEntity.getAuthorNames()).isEmpty();
        }
    }

    @Test
    void setBookMetadata_updatesLocks() {
        BookMetadata newMeta = BookMetadata.builder().title("T").titleLocked(true).publisherLocked(false).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getTitleLocked()).isTrue();
            assertThat(metadataEntity.getPublisherLocked()).isFalse();
        }
    }

    @Test
    void setBookMetadata_nullReplaceMode_setsValueWhenNotNull() {
        metadataEntity.setPublisher("Existing");
        BookMetadata newMeta = BookMetadata.builder().title("T").publisher("New").build();
        MetadataUpdateContext context = buildContext(newMeta, null);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getPublisher()).isEqualTo("New");
        }
    }

    @Test
    void setBookMetadata_nullReplaceMode_doesNotSetNullValue() {
        metadataEntity.setPublisher("Existing");
        BookMetadata newMeta = BookMetadata.builder().title("T").publisher(null).build();
        MetadataUpdateContext context = buildContext(newMeta, null);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getPublisher()).isEqualTo("Existing");
        }
    }

    @Test
    void setBookMetadata_blankStringTreatedAsNull() {
        metadataEntity.setIsbn13(null);
        BookMetadata newMeta = BookMetadata.builder().title("T").isbn13("  ").build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            assertThat(metadataEntity.getIsbn13()).isNull();
        }
    }

    @Test
    void updateFileNameIfConverted_noChangeWhenOriginalExists() {
        BookFileEntity bookFile = BookFileEntity.builder().id(10L).fileName("test.cbr").build();
        java.nio.file.Path tempDir;
        try {
            tempDir = java.nio.file.Files.createTempDirectory("test-metadata-");
            java.nio.file.Path original = tempDir.resolve("test.cbr");
            java.nio.file.Files.createFile(original);

            updater.updateFileNameIfConverted(bookFile, original);

            assertThat(bookFile.getFileName()).isEqualTo("test.cbr");
            verify(jooqBookRepository, never()).updateFileNameAndSubPath(anyLong(), any(), any());
            java.nio.file.Files.deleteIfExists(original);
            java.nio.file.Files.deleteIfExists(tempDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void updateFileNameIfConverted_updatesToCbzWhenOriginalGoneAndCbzExists() {
        BookFileEntity bookFile = BookFileEntity.builder().id(10L).fileName("test.cbr").build();
        try {
            java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-");
            java.nio.file.Path original = tempDir.resolve("test.cbr");
            java.nio.file.Path cbz = tempDir.resolve("test.cbz");
            java.nio.file.Files.createFile(cbz);

            updater.updateFileNameIfConverted(bookFile, original);

            assertThat(bookFile.getFileName()).isEqualTo("test.cbz");
            verify(jooqBookRepository).updateFileNameAndSubPath(10L, "test.cbz", null);
            java.nio.file.Files.deleteIfExists(cbz);
            java.nio.file.Files.deleteIfExists(tempDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void setBookMetadata_categoriesLocked_notUpdated() {
        metadataEntity.setCategoriesLocked(true);

        BookMetadata newMeta = BookMetadata.builder().title("T").categories(Set.of("New Cat")).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository, never()).setCategoriesForBook(anyLong(), any());
            verify(relationsRepository, never()).clearCategoriesForBook(anyLong());
        }
    }

    @Test
    void setBookMetadata_moodsLocked_notUpdated() {
        metadataEntity.setMoodsLocked(true);

        BookMetadata newMeta = BookMetadata.builder().title("T").moods(Set.of("New Mood")).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository, never()).setMoodsForBook(anyLong(), any());
            verify(relationsRepository, never()).clearMoodsForBook(anyLong());
        }
    }

    @Test
    void setBookMetadata_tagsLocked_notUpdated() {
        metadataEntity.setTagsLocked(true);

        BookMetadata newMeta = BookMetadata.builder().title("T").tags(Set.of("New Tag")).build();
        MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository, never()).setTagsForBook(anyLong(), any());
            verify(relationsRepository, never()).clearTagsForBook(anyLong());
        }
    }

    @Test
    void setBookMetadata_categoriesReplaceMissing_skipsWhenPresent() {
        when(relationsRepository.findCategoryNamesByBookId(1L)).thenReturn(Set.of("Existing"));

        BookMetadata newMeta = BookMetadata.builder().title("T").categories(Set.of("New Cat")).build();
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).build())
                .replaceMode(MetadataReplaceMode.REPLACE_MISSING)
                .build();

        try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
            mockSettingsAndChangeDetector(mcd, true, true);

            updater.setBookMetadata(context);

            verify(relationsRepository, never()).setCategoriesForBook(anyLong(), any());
        }
    }

    @Nested
    class BasicFieldUpdates {

        @Test
        void updatesPublishedDate() {
            LocalDate date = LocalDate.of(2024, 6, 15);
            BookMetadata newMeta = BookMetadata.builder().title("T").publishedDate(date).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getPublishedDate()).isEqualTo(date);
            }
        }

        @Test
        void updatesPageCount() {
            BookMetadata newMeta = BookMetadata.builder().title("T").pageCount(350).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getPageCount()).isEqualTo(350);
            }
        }

        @Test
        void updatesGoodreadsRating() {
            BookMetadata newMeta = BookMetadata.builder().title("T").goodreadsRating(4.5).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getGoodreadsRating()).isEqualTo(4.5);
            }
        }

        @Test
        void updatesSeriesFields() {
            BookMetadata newMeta = BookMetadata.builder()
                    .title("T").seriesName("Foundation").seriesNumber(1.0f).seriesTotal(7).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getSeriesName()).isEqualTo("Foundation");
                assertThat(metadataEntity.getSeriesNumber()).isEqualTo(1.0f);
                assertThat(metadataEntity.getSeriesTotal()).isEqualTo(7);
            }
        }

        @Test
        void updatesDescription() {
            BookMetadata newMeta = BookMetadata.builder().title("T").description("A great book").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getDescription()).isEqualTo("A great book");
            }
        }

        @Test
        void updatesIdentifierFields() {
            BookMetadata newMeta = BookMetadata.builder().title("T")
                    .isbn13("9781234567890").isbn10("1234567890").asin("B01ABCDE")
                    .goodreadsId("GR123").googleId("GOOG1").hardcoverId("HC1")
                    .comicvineId("CV1").lubimyczytacId("LUB1").ranobedbId("RDB1")
                    .audibleId("AUD1").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getIsbn13()).isEqualTo("9781234567890");
                assertThat(metadataEntity.getIsbn10()).isEqualTo("1234567890");
                assertThat(metadataEntity.getAsin()).isEqualTo("B01ABCDE");
                assertThat(metadataEntity.getGoodreadsId()).isEqualTo("GR123");
                assertThat(metadataEntity.getGoogleId()).isEqualTo("GOOG1");
                assertThat(metadataEntity.getHardcoverId()).isEqualTo("HC1");
                assertThat(metadataEntity.getComicvineId()).isEqualTo("CV1");
                assertThat(metadataEntity.getLubimyczytacId()).isEqualTo("LUB1");
                assertThat(metadataEntity.getRanobedbId()).isEqualTo("RDB1");
                assertThat(metadataEntity.getAudibleId()).isEqualTo("AUD1");
            }
        }

        @Test
        void updatesRatingAndReviewFields() {
            BookMetadata newMeta = BookMetadata.builder().title("T")
                    .amazonRating(4.2).amazonReviewCount(100)
                    .hardcoverRating(3.8).hardcoverReviewCount(50)
                    .goodreadsReviewCount(200)
                    .lubimyczytacRating(4.0)
                    .ranobedbRating(3.5)
                    .audibleRating(4.7).audibleReviewCount(80)
                    .build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getAmazonRating()).isEqualTo(4.2);
                assertThat(metadataEntity.getAmazonReviewCount()).isEqualTo(100);
                assertThat(metadataEntity.getHardcoverRating()).isEqualTo(3.8);
                assertThat(metadataEntity.getHardcoverReviewCount()).isEqualTo(50);
                assertThat(metadataEntity.getGoodreadsReviewCount()).isEqualTo(200);
                assertThat(metadataEntity.getLubimyczytacRating()).isEqualTo(4.0);
                assertThat(metadataEntity.getRanobedbRating()).isEqualTo(3.5);
                assertThat(metadataEntity.getAudibleRating()).isEqualTo(4.7);
                assertThat(metadataEntity.getAudibleReviewCount()).isEqualTo(80);
            }
        }

        @Test
        void updatesAgeRatingAndContentRating() {
            BookMetadata newMeta = BookMetadata.builder().title("T")
                    .ageRating(18).contentRating("Mature").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getAgeRating()).isEqualTo(18);
                assertThat(metadataEntity.getContentRating()).isEqualTo("Mature");
            }
        }

    }

    @Nested
    class AudiobookMetadataUpdates {

        @Test
        void updatesNarrator() {
            BookMetadata newMeta = BookMetadata.builder().title("T").narrator("John Smith").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getNarrator()).isEqualTo("John Smith");
            }
        }

        @Test
        void updatesAbridged() {
            BookMetadata newMeta = BookMetadata.builder().title("T").abridged(true).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getAbridged()).isTrue();
            }
        }

        @Test
        void narratorLocked_notUpdated() {
            metadataEntity.setNarratorLocked(true);
            metadataEntity.setNarrator("Locked Narrator");
            BookMetadata newMeta = BookMetadata.builder().title("T").narrator("New Narrator").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getNarrator()).isEqualTo("Locked Narrator");
            }
        }

        @Test
        void blankNarrator_setsNull() {
            BookMetadata newMeta = BookMetadata.builder().title("T").narrator("  ").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getNarrator()).isNull();
            }
        }

        @Test
        void clearNarrator_setsNull() {
            metadataEntity.setNarrator("Narrator");
            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataClearFlags clearFlags = new MetadataClearFlags();
            clearFlags.setNarrator(true);

            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(bookEntity)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).clearFlags(clearFlags).build())
                    .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                    .build();

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getNarrator()).isNull();
            }
        }
    }

    @Nested
    class ComicMetadataUpdates {

        @Test
        void skipsWhenComicMetadataNull() {
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(null).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                assertThat(metadataEntity.getComicMetadata()).isNull();
            }
        }

        @Test
        void createsComicEntityWhenNullAndHasData() {
            ComicMetadata comicDto = ComicMetadata.builder().issueNumber("5").build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(comicRelationsRepository).insertBlankRow(1L);
            }
        }

        @Test
        void skipsCreationWhenNoDataAndNoLocks() {
            ComicMetadata comicDto = ComicMetadata.builder().build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(comicRelationsRepository, never()).insertBlankRow(anyLong());
            }
        }

        @Test
        void createsComicEntityWhenHasLocks() {
            ComicMetadata comicDto = ComicMetadata.builder().issueNumberLocked(true).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(comicRelationsRepository).insertBlankRow(1L);
            }
        }

        @Test
        void updatesBasicComicFields() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            ComicMetadata comicDto = ComicMetadata.builder()
                    .issueNumber("10").volumeName("Vol 2").volumeNumber(2)
                    .storyArc("Civil War").storyArcNumber(3)
                    .alternateSeries("Alt").alternateIssue("A1")
                    .imprint("Marvel").format("Standard")
                    .blackAndWhite(true).manga(false)
                    .readingDirection("rtl").webLink("http://example.com")
                    .notes("Some notes")
                    .build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                assertThat(existing.getIssueNumber()).isEqualTo("10");
                assertThat(existing.getVolumeName()).isEqualTo("Vol 2");
                assertThat(existing.getVolumeNumber()).isEqualTo(2);
                assertThat(existing.getStoryArc()).isEqualTo("Civil War");
                assertThat(existing.getStoryArcNumber()).isEqualTo(3);
                assertThat(existing.getAlternateSeries()).isEqualTo("Alt");
                assertThat(existing.getAlternateIssue()).isEqualTo("A1");
                assertThat(existing.getImprint()).isEqualTo("Marvel");
                assertThat(existing.getFormat()).isEqualTo("Standard");
                assertThat(existing.getBlackAndWhite()).isTrue();
                assertThat(existing.getManga()).isFalse();
                assertThat(existing.getReadingDirection()).isEqualTo("rtl");
                assertThat(existing.getWebLink()).isEqualTo("http://example.com");
                assertThat(existing.getNotes()).isEqualTo("Some notes");
            }
        }

        @Test
        void updatesComicLocks() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            ComicMetadata comicDto = ComicMetadata.builder()
                    .issueNumberLocked(true).volumeNameLocked(true)
                    .charactersLocked(true).pencillersLocked(true)
                    .build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                assertThat(existing.getIssueNumberLocked()).isTrue();
                assertThat(existing.getVolumeNameLocked()).isTrue();
                assertThat(existing.getCharactersLocked()).isTrue();
                assertThat(existing.getPencillersLocked()).isTrue();
            }
        }

        @Test
        void updatesCharacters_replaceAll() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            when(comicRelationsRepository.findCharacterNamesByBookId(1L)).thenReturn(Set.of("Batman"));
            ComicMetadata comicDto = ComicMetadata.builder().characters(Set.of("Superman")).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository).setCharactersForBook(1L, Set.of("Superman"));
                assertThat(existing.getCharacterNames()).containsExactly("Superman");
            }
        }

        @Test
        void updatesCharacters_replaceMissing_skipsWhenPresent() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            when(comicRelationsRepository.findCharacterNamesByBookId(1L)).thenReturn(Set.of("Batman"));
            ComicMetadata comicDto = ComicMetadata.builder().characters(Set.of("Superman")).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository, never()).setCharactersForBook(eq(1L), any());
                assertThat(existing.getCharacterNames()).containsExactly("Batman");
            }
        }

        @Test
        void updatesTeams_replaceAll() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            ComicMetadata comicDto = ComicMetadata.builder().teams(Set.of("Avengers")).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository).setTeamsForBook(1L, Set.of("Avengers"));
                assertThat(existing.getTeamNames()).containsExactly("Avengers");
            }
        }

        @Test
        void updatesLocations_replaceAll() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            ComicMetadata comicDto = ComicMetadata.builder().locations(Set.of("Gotham")).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository).setLocationsForBook(1L, Set.of("Gotham"));
                assertThat(existing.getLocationNames()).containsExactly("Gotham");
            }
        }

        @Test
        void updatesCreators_pencillers() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            ComicMetadata comicDto = ComicMetadata.builder().pencillers(Set.of("Jim Lee")).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository).setCreatorsForBookRole(1L, ComicCreatorRole.PENCILLER, Set.of("Jim Lee"));
                assertThat(existing.getPencillerNames()).containsExactly("Jim Lee");
            }
        }

        @Test
        void creatorRole_locked_notUpdated() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).pencillersLocked(true).build();
            metadataEntity.setComicMetadata(existing);

            ComicMetadata comicDto = ComicMetadata.builder().pencillers(Set.of("Jim Lee")).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository, never()).setCreatorsForBookRole(eq(1L), eq(ComicCreatorRole.PENCILLER), any());
            }
        }

        @Test
        void emptyCharacters_replaceAll_clearsExisting() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            when(comicRelationsRepository.findCharacterNamesByBookId(1L)).thenReturn(Set.of("Batman"));
            ComicMetadata comicDto = ComicMetadata.builder().characters(Set.of()).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository).clearCharactersForBook(1L);
                assertThat(existing.getCharacterNames()).isEmpty();
            }
        }

        @Test
        void charactersLocked_notUpdated() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).charactersLocked(true).build();
            metadataEntity.setComicMetadata(existing);

            when(comicRelationsRepository.findCharacterNamesByBookId(1L)).thenReturn(Set.of("Batman"));
            ComicMetadata comicDto = ComicMetadata.builder().characters(Set.of("Superman")).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                assertThat(existing.getCharacterNames()).containsExactly("Batman");
            }
        }

        @Test
        void deletesOrphanedEntitiesAfterUpdate() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            ComicMetadata comicDto = ComicMetadata.builder().issueNumber("1").build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository).deleteOrphanedCharacters();
                verify(comicRelationsRepository).deleteOrphanedTeams();
                verify(comicRelationsRepository).deleteOrphanedLocations();
                verify(comicRelationsRepository).deleteOrphanedCreators();
            }
        }

        @Test
        void emptyCreators_replaceAll_removesExistingForRole() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            when(comicRelationsRepository.findCreatorNamesByBookIdAndRole(1L, ComicCreatorRole.PENCILLER)).thenReturn(Set.of("Artist"));
            ComicMetadata comicDto = ComicMetadata.builder().pencillers(Set.of()).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository).clearCreatorsForBookRole(1L, ComicCreatorRole.PENCILLER);
                assertThat(existing.getPencillerNames()).isEmpty();
            }
        }

        @Test
        void teams_replaceMissing_skipsWhenPresent() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            when(comicRelationsRepository.findTeamNamesByBookId(1L)).thenReturn(Set.of("X-Men"));
            ComicMetadata comicDto = ComicMetadata.builder().teams(Set.of("Avengers")).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository, never()).setTeamsForBook(eq(1L), any());
                assertThat(existing.getTeamNames()).containsExactly("X-Men");
            }
        }

        @Test
        void locations_replaceMissing_skipsWhenPresent() {
            ComicMetadataEntity existing = ComicMetadataEntity.builder().bookId(1L).build();
            metadataEntity.setComicMetadata(existing);

            when(comicRelationsRepository.findLocationNamesByBookId(1L)).thenReturn(Set.of("Gotham"));
            ComicMetadata comicDto = ComicMetadata.builder().locations(Set.of("Metropolis")).build();
            BookMetadata newMeta = BookMetadata.builder().title("T").comicMetadata(comicDto).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(comicRelationsRepository, never()).setLocationsForBook(eq(1L), any());
                assertThat(existing.getLocationNames()).containsExactly("Gotham");
            }
        }
    }

    @Nested
    class CollectionReplaceModes {

        @Test
        void moods_replaceMissing_addsWhenEmpty() {
            when(relationsRepository.findMoodNamesByBookId(1L)).thenReturn(Set.of());

            BookMetadata newMeta = BookMetadata.builder().title("T").moods(Set.of("Happy")).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).setMoodsForBook(1L, Set.of("Happy"));
            }
        }

        @Test
        void moods_replaceMissing_skipsWhenPresent() {
            when(relationsRepository.findMoodNamesByBookId(1L)).thenReturn(Set.of("Sad"));

            BookMetadata newMeta = BookMetadata.builder().title("T").moods(Set.of("Happy")).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository, never()).setMoodsForBook(anyLong(), any());
            }
        }

        @Test
        void tags_replaceMissing_addsWhenEmpty() {
            when(relationsRepository.findTagNamesByBookId(1L)).thenReturn(Set.of());

            BookMetadata newMeta = BookMetadata.builder().title("T").tags(Set.of("SciFi")).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).setTagsForBook(1L, Set.of("SciFi"));
            }
        }

        @Test
        void tags_replaceMissing_skipsWhenPresent() {
            when(relationsRepository.findTagNamesByBookId(1L)).thenReturn(Set.of("Fantasy"));

            BookMetadata newMeta = BookMetadata.builder().title("T").tags(Set.of("SciFi")).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_MISSING);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository, never()).setTagsForBook(anyLong(), any());
            }
        }

        @Test
        void categories_replaceWhenProvided_replacesExisting() {
            when(relationsRepository.findCategoryNamesByBookId(1L)).thenReturn(Set.of("Old"));

            BookMetadata newMeta = BookMetadata.builder().title("T").categories(Set.of("New")).build();
            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(bookEntity)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).build())
                    .replaceMode(MetadataReplaceMode.REPLACE_WHEN_PROVIDED)
                    .mergeCategories(false)
                    .build();

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).setCategoriesForBook(1L, Set.of("New"));
            }
        }

        @Test
        void moods_replaceWhenProvided_noMerge_replacesExisting() {
            when(relationsRepository.findMoodNamesByBookId(1L)).thenReturn(Set.of("Old"));

            BookMetadata newMeta = BookMetadata.builder().title("T").moods(Set.of("New")).build();
            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(bookEntity)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).build())
                    .replaceMode(MetadataReplaceMode.REPLACE_WHEN_PROVIDED)
                    .mergeMoods(false)
                    .build();

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).setMoodsForBook(1L, Set.of("New"));
            }
        }

        @Test
        void tags_replaceWhenProvided_noMerge_replacesExisting() {
            when(relationsRepository.findTagNamesByBookId(1L)).thenReturn(Set.of("Old"));

            BookMetadata newMeta = BookMetadata.builder().title("T").tags(Set.of("New")).build();
            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(bookEntity)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).build())
                    .replaceMode(MetadataReplaceMode.REPLACE_WHEN_PROVIDED)
                    .mergeTags(false)
                    .build();

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).setTagsForBook(1L, Set.of("New"));
            }
        }

        @Test
        void authors_nullReplaceMode_noMerge_replacesExisting() {
            when(relationsRepository.findAuthorNamesByBookId(1L)).thenReturn(List.of("Old"));

            BookMetadata newMeta = BookMetadata.builder().title("T").authors(List.of("New")).build();
            MetadataUpdateContext context = buildContext(newMeta, null);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).setAuthorsForBook(1L, List.of("New"));
            }
        }

        @Test
        void categories_nullReplaceMode_replacesExisting() {
            when(relationsRepository.findCategoryNamesByBookId(1L)).thenReturn(Set.of("Old"));

            BookMetadata newMeta = BookMetadata.builder().title("T").categories(Set.of("New")).build();
            MetadataUpdateContext context = buildContext(newMeta, null);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).setCategoriesForBook(1L, Set.of("New"));
            }
        }

        @Test
        void moods_nullReplaceMode_replacesExisting() {
            when(relationsRepository.findMoodNamesByBookId(1L)).thenReturn(Set.of("Old"));

            BookMetadata newMeta = BookMetadata.builder().title("T").moods(Set.of("New")).build();
            MetadataUpdateContext context = buildContext(newMeta, null);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).setMoodsForBook(1L, Set.of("New"));
            }
        }

        @Test
        void tags_nullReplaceMode_replacesExisting() {
            when(relationsRepository.findTagNamesByBookId(1L)).thenReturn(Set.of("Old"));

            BookMetadata newMeta = BookMetadata.builder().title("T").tags(Set.of("New")).build();
            MetadataUpdateContext context = buildContext(newMeta, null);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).setTagsForBook(1L, Set.of("New"));
            }
        }

        @Test
        void clearMoods_clearsSet() {
            when(relationsRepository.findMoodNamesByBookId(1L)).thenReturn(Set.of("Mood"));

            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataClearFlags clearFlags = new MetadataClearFlags();
            clearFlags.setMoods(true);

            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(bookEntity)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).clearFlags(clearFlags).build())
                    .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                    .build();

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).clearMoodsForBook(1L);
            }
        }

        @Test
        void clearTags_clearsSet() {
            when(relationsRepository.findTagNamesByBookId(1L)).thenReturn(Set.of("Tag"));

            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataClearFlags clearFlags = new MetadataClearFlags();
            clearFlags.setTags(true);

            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(bookEntity)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).clearFlags(clearFlags).build())
                    .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                    .build();

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).clearTagsForBook(1L);
            }
        }

        @Test
        void clearCategories_clearsSet() {
            when(relationsRepository.findCategoryNamesByBookId(1L)).thenReturn(Set.of("Cat"));

            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataClearFlags clearFlags = new MetadataClearFlags();
            clearFlags.setCategories(true);

            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(bookEntity)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(newMeta).clearFlags(clearFlags).build())
                    .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                    .build();

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).clearCategoriesForBook(1L);
            }
        }

        @Test
        void emptyCategories_replaceAll_clearsExisting() {
            when(relationsRepository.findCategoryNamesByBookId(1L)).thenReturn(Set.of("Cat"));

            BookMetadata newMeta = BookMetadata.builder().title("T").categories(Set.of()).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).clearCategoriesForBook(1L);
            }
        }

        @Test
        void emptyMoods_replaceAll_clearsExisting() {
            when(relationsRepository.findMoodNamesByBookId(1L)).thenReturn(Set.of("Mood"));

            BookMetadata newMeta = BookMetadata.builder().title("T").moods(Set.of()).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).clearMoodsForBook(1L);
            }
        }

        @Test
        void emptyTags_replaceAll_clearsExisting() {
            when(relationsRepository.findTagNamesByBookId(1L)).thenReturn(Set.of("Tag"));

            BookMetadata newMeta = BookMetadata.builder().title("T").tags(Set.of()).build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(relationsRepository).clearTagsForBook(1L);
            }
        }
    }

    @Nested
    class MatchScoreAndDelegation {

        @Test
        void calculatesMatchScoreAfterUpdate() {
            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            when(metadataMatchService.calculateMatchScore(bookEntity)).thenReturn(0.85f);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(metadataMatchService).calculateMatchScore(bookEntity);
                assertThat(bookEntity.getMetadataMatchScore()).isEqualTo(0.85f);
            }
        }

        @Test
        void matchScoreExceptionDoesNotFailUpdate() {
            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            when(metadataMatchService.calculateMatchScore(bookEntity)).thenThrow(new RuntimeException("calc error"));

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);

                verify(jooqBookMetadataRepository).updateFullRow(metadataEntity);
            }
        }

        @Test
        void delegatesReviewUpdate() {
            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                updater.setBookMetadata(context);
                verify(bookReviewUpdateService).updateBookReviews(any(), any(), any(), anyBoolean());
            }
        }

        @Test
        void sidecarWriter_calledWhenEnabled() {
            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                MetadataPersistenceSettings settings = MetadataPersistenceSettings.builder()
                        .saveToOriginalFile(MetadataPersistenceSettings.SaveToOriginalFile.builder().build())
                        .build();
                when(appSettingService.getAppSettings()).thenReturn(
                        AppSettings.builder().metadataPersistenceSettings(settings).build());
                when(sidecarMetadataWriter.isWriteOnUpdateEnabled()).thenReturn(true);

                mcd.when(() -> MetadataChangeDetector.isDifferent(any(), any(), any())).thenReturn(true);
                mcd.when(() -> MetadataChangeDetector.hasValueChanges(any(), any(), any())).thenReturn(true);
                mcd.when(() -> MetadataChangeDetector.hasLockChanges(any(), any())).thenReturn(false);
                mcd.when(() -> MetadataChangeDetector.hasValueChangesForFileWrite(any(), any(), any())).thenReturn(false);

                updater.setBookMetadata(context);

                verify(sidecarMetadataWriter).writeSidecarMetadata(bookEntity);
            }
        }

        @Test
        void sidecarWriter_exceptionDoesNotFailUpdate() {
            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                MetadataPersistenceSettings settings = MetadataPersistenceSettings.builder()
                        .saveToOriginalFile(MetadataPersistenceSettings.SaveToOriginalFile.builder().build())
                        .build();
                when(appSettingService.getAppSettings()).thenReturn(
                        AppSettings.builder().metadataPersistenceSettings(settings).build());
                when(sidecarMetadataWriter.isWriteOnUpdateEnabled()).thenReturn(true);
                doThrow(new RuntimeException("sidecar error")).when(sidecarMetadataWriter).writeSidecarMetadata(any());

                mcd.when(() -> MetadataChangeDetector.isDifferent(any(), any(), any())).thenReturn(true);
                mcd.when(() -> MetadataChangeDetector.hasValueChanges(any(), any(), any())).thenReturn(true);
                mcd.when(() -> MetadataChangeDetector.hasLockChanges(any(), any())).thenReturn(false);
                mcd.when(() -> MetadataChangeDetector.hasValueChangesForFileWrite(any(), any(), any())).thenReturn(false);

                updater.setBookMetadata(context);

                verify(jooqBookMetadataRepository).updateFullRow(metadataEntity);
            }
        }
    }

    @Nested
    class FileMoveAfterUpdate {

        @Test
        void movesFile_whenSettingEnabled() {
            BookFileEntity primaryFile = BookFileEntity.builder()
                    .fileName("book.epub").bookType(BookFileType.EPUB).build();
            bookEntity.setBookFiles(new ArrayList<>(List.of(primaryFile)));

            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            FileMoveResult moveResult = FileMoveResult.builder()
                    .moved(true).newFileName("new-book.epub").newFileSubPath("Author/new-book.epub").build();

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                MetadataPersistenceSettings settings = MetadataPersistenceSettings.builder()
                        .saveToOriginalFile(MetadataPersistenceSettings.SaveToOriginalFile.builder().build())
                        .moveFilesToLibraryPattern(true)
                        .build();
                when(appSettingService.getAppSettings()).thenReturn(
                        AppSettings.builder().metadataPersistenceSettings(settings).build());
                when(sidecarMetadataWriter.isWriteOnUpdateEnabled()).thenReturn(false);
                when(fileMoveService.moveSingleFile(any())).thenReturn(moveResult);

                mcd.when(() -> MetadataChangeDetector.isDifferent(any(), any(), any())).thenReturn(true);
                mcd.when(() -> MetadataChangeDetector.hasValueChanges(any(), any(), any())).thenReturn(true);
                mcd.when(() -> MetadataChangeDetector.hasLockChanges(any(), any())).thenReturn(false);
                mcd.when(() -> MetadataChangeDetector.hasValueChangesForFileWrite(any(), any(), any())).thenReturn(false);

                updater.setBookMetadata(context);

                verify(fileMoveService).moveSingleFile(any());
            }
        }

        @Test
        void moveFileException_doesNotFailUpdate() {
            BookFileEntity primaryFile = BookFileEntity.builder()
                    .fileName("book.epub").bookType(BookFileType.EPUB).build();
            bookEntity.setBookFiles(new ArrayList<>(List.of(primaryFile)));

            BookMetadata newMeta = BookMetadata.builder().title("T").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                MetadataPersistenceSettings settings = MetadataPersistenceSettings.builder()
                        .saveToOriginalFile(MetadataPersistenceSettings.SaveToOriginalFile.builder().build())
                        .moveFilesToLibraryPattern(true)
                        .build();
                when(appSettingService.getAppSettings()).thenReturn(
                        AppSettings.builder().metadataPersistenceSettings(settings).build());
                when(sidecarMetadataWriter.isWriteOnUpdateEnabled()).thenReturn(false);
                when(fileMoveService.moveSingleFile(any())).thenThrow(new RuntimeException("move error"));

                mcd.when(() -> MetadataChangeDetector.isDifferent(any(), any(), any())).thenReturn(true);
                mcd.when(() -> MetadataChangeDetector.hasValueChanges(any(), any(), any())).thenReturn(true);
                mcd.when(() -> MetadataChangeDetector.hasLockChanges(any(), any())).thenReturn(false);
                mcd.when(() -> MetadataChangeDetector.hasValueChangesForFileWrite(any(), any(), any())).thenReturn(false);

                updater.setBookMetadata(context);

                verify(jooqBookMetadataRepository).updateFullRow(metadataEntity);
            }
        }
    }

    @Nested
    class FileNameConversion {

        @Test
        void noChangeWhenNeitherOriginalNorCbzExists() {
            BookFileEntity bookFile = BookFileEntity.builder().id(11L).fileName("test.cbr").build();
            try {
                java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-");
                java.nio.file.Path original = tempDir.resolve("test.cbr");

                updater.updateFileNameIfConverted(bookFile, original);

                assertThat(bookFile.getFileName()).isEqualTo("test.cbr");
                verify(jooqBookRepository, never()).updateFileNameAndSubPath(anyLong(), any(), any());
                java.nio.file.Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void handlesFileNameWithNoExtension() {
            BookFileEntity bookFile = BookFileEntity.builder().id(11L).fileName("testfile").build();
            try {
                java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-");
                java.nio.file.Path original = tempDir.resolve("testfile");
                java.nio.file.Path cbz = tempDir.resolve("testfile.cbz");
                java.nio.file.Files.createFile(cbz);

                updater.updateFileNameIfConverted(bookFile, original);

                assertThat(bookFile.getFileName()).isEqualTo("testfile.cbz");
                verify(jooqBookRepository).updateFileNameAndSubPath(11L, "testfile.cbz", null);
                java.nio.file.Files.deleteIfExists(cbz);
                java.nio.file.Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    class NetworkStorageGating {

        @Test
        void updateBookMetadata_networkStorage_skipsFileWrite() {
            when(appProperties.isLocalStorage()).thenReturn(false);

            BookFileEntity bookFile = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB)
                    .isBookFormat(true)
                    .build();
            bookEntity.setBookFiles(new ArrayList<>(List.of(bookFile)));

            BookMetadata newMeta = BookMetadata.builder().title("New Title").build();
            MetadataUpdateContext context = buildContext(newMeta, MetadataReplaceMode.REPLACE_ALL);

            try (MockedStatic<MetadataChangeDetector> mcd = mockStatic(MetadataChangeDetector.class)) {
                mockSettingsAndChangeDetector(mcd, true, true);
                mcd.when(() -> MetadataChangeDetector.hasValueChangesForFileWrite(any(), any(), any())).thenReturn(true);

                updater.setBookMetadata(context);

                verify(metadataWriterFactory, never()).getWriter(any());
                verify(jooqBookMetadataRepository).updateFullRow(metadataEntity);
            }
        }
    }
}
