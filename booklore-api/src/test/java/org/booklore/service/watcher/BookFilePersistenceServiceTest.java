package org.booklore.service.watcher;

import org.booklore.mapper.BookMapper;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.repository.jooq.dto.LibraryPathRow;
import org.booklore.repository.jooq.JooqLibraryPathRepository;
import org.booklore.repository.LibraryPathRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.NotificationService;
import org.booklore.util.FileUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.booklore.service.watcher.PendingDeletionPool.BookSnapshot;
import org.booklore.service.watcher.PendingDeletionPool.FileSnapshot;

class BookFilePersistenceServiceTest {

    @Mock private EntityManager entityManager;
    @Mock private BookRepository bookRepository;
    @Mock private BookFileRepository bookFileRepository;
    @Mock private BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock private JooqBookRepository jooqBookRepository;
    @Mock private LibraryPathRepository libraryPathRepository;
    @Mock private JooqLibraryPathRepository jooqLibraryPathRepository;
    @Mock private NotificationService notificationService;
    @Mock private BookMapper bookMapper;

    private BookFilePersistenceService service;

    private AutoCloseable mocks;
    private MockedStatic<FileUtils> fileUtilsMock;

    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new BookFilePersistenceService(entityManager, bookRepository, bookFileRepository, bookAdditionalFileRepository, jooqBookRepository, libraryPathRepository, jooqLibraryPathRepository, notificationService, bookMapper);

        fileUtilsMock = mockStatic(FileUtils.class);
        fileUtilsMock.when(() -> FileUtils.getRelativeSubPath(anyString(), any(Path.class))).thenReturn("sub");

        libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath("/library");

        library = LibraryEntity.builder()
                .id(1L)
                .name("Test Library")
                .build();

        // paths are read via jOOQ now, not off the LAZY entity collection
        when(jooqLibraryPathRepository.findPathsByLibraryId(1L))
                .thenReturn(List.of(new LibraryPathRow(1L, "/library")));
        when(libraryPathRepository.findById(1L)).thenReturn(Optional.of(libraryPath));
    }

    @AfterEach
    void tearDown() throws Exception {
        fileUtilsMock.close();
        mocks.close();
    }

    private BookEntity buildBook(Long id) {
        return BookEntity.builder()
                .id(id)
                .library(library)
                .libraryPath(libraryPath)
                .deleted(false)
                .bookFiles(new ArrayList<>())
                .build();
    }

    private BookFileEntity buildBookFile(Long id, BookEntity book, String fileName, String hash) {
        return BookFileEntity.builder()
                .id(id)
                .book(book)
                .fileName(fileName)
                .fileSubPath("sub")
                .currentHash(hash)
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .build();
    }

    @Nested
    class FindMatchingLibraryPath {

        @Test
        void matchesFileUnderLibraryPath() {
            String result = service.findMatchingLibraryPath(library, Path.of("/library/sub/test.epub"));

            assertThat(result).isEqualTo("/library");
        }

        @Test
        void throwsWhenNoMatchingPath() {
            assertThatThrownBy(() -> service.findMatchingLibraryPath(library, Path.of("/other/test.epub")))
                    .isInstanceOf(Exception.class);
        }

        @Test
        void matchesDeeperNestedFile() {
            String result = service.findMatchingLibraryPath(library, Path.of("/library/a/b/c/test.epub"));

            assertThat(result).isEqualTo("/library");
        }
    }

    @Nested
    class GetLibraryPathEntityForFile {

        @Test
        void returnsMatchingPathEntity() {
            LibraryPathEntity result = service.getLibraryPathEntityForFile(library, "/library/sub/test.epub");

            assertThat(result).isEqualTo(libraryPath);
        }

        @Test
        void throwsWhenNoMatchingPathEntity() {
            assertThatThrownBy(() -> service.getLibraryPathEntityForFile(library, "/other/test.epub"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        void picksLongestMatchWhenMultiplePaths() {
            LibraryPathEntity deeperPath = new LibraryPathEntity();
            deeperPath.setId(2L);
            deeperPath.setPath("/library/sub");
            when(jooqLibraryPathRepository.findPathsByLibraryId(1L)).thenReturn(
                    List.of(new LibraryPathRow(1L, "/library"), new LibraryPathRow(2L, "/library/sub")));
            when(libraryPathRepository.findById(2L)).thenReturn(Optional.of(deeperPath));

            LibraryPathEntity result = service.getLibraryPathEntityForFile(library, "/library/sub/test.epub");

            assertThat(result).isEqualTo(deeperPath);
        }
    }

    @Nested
    class UpdatePathIfChanged {

        @Test
        void updatesPathWhenSubPathDiffers() {
            BookEntity book = buildBook(10L);
            BookFileEntity bookFile = buildBookFile(100L, book, "test.epub", "hash123");
            book.setBookFiles(new ArrayList<>(List.of(bookFile)));

            when(entityManager.merge(any(LibraryPathEntity.class))).thenReturn(libraryPath);
            fileUtilsMock.when(() -> FileUtils.getRelativeSubPath(anyString(), any(Path.class))).thenReturn("newsub");

            service.updatePathIfChanged(book, library, Path.of("/library/newsub/test.epub"), "hash123");

            verify(jooqBookRepository).updateLibrary(10L, 1L, 1L);
            verify(bookAdditionalFileRepository).updateFileNameAndSubPath(100L, "test.epub", "newsub");
            verify(jooqBookRepository).markUndeleted(List.of(10L));
            assertThat(bookFile.getFileSubPath()).isEqualTo("newsub");
        }

        @Test
        void updatesPathWhenFileNameDiffers() {
            BookEntity book = buildBook(10L);
            BookFileEntity bookFile = buildBookFile(100L, book, "old.epub", "hash123");
            book.setBookFiles(new ArrayList<>(List.of(bookFile)));

            when(entityManager.merge(any(LibraryPathEntity.class))).thenReturn(libraryPath);

            service.updatePathIfChanged(book, library, Path.of("/library/sub/new.epub"), "hash123");

            verify(jooqBookRepository).updateLibrary(10L, 1L, 1L);
            verify(bookAdditionalFileRepository).updateFileNameAndSubPath(100L, "new.epub", "sub");
            verify(jooqBookRepository).markUndeleted(List.of(10L));
            assertThat(bookFile.getFileName()).isEqualTo("new.epub");
        }

        @Test
        void undeleteBookOnPathUpdate() {
            BookEntity book = buildBook(10L);
            book.setDeleted(true);
            BookFileEntity bookFile = buildBookFile(100L, book, "test.epub", "hash123");
            book.setBookFiles(new ArrayList<>(List.of(bookFile)));

            when(entityManager.merge(any(LibraryPathEntity.class))).thenReturn(libraryPath);

            service.updatePathIfChanged(book, library, Path.of("/library/sub/test.epub"), "hash123");

            verify(jooqBookRepository).updateLibrary(10L, 1L, 1L);
            verify(bookAdditionalFileRepository).updateFileNameAndSubPath(100L, "test.epub", "sub");
            verify(jooqBookRepository).markUndeleted(List.of(10L));
            assertThat(book.getDeleted()).isFalse();
            assertThat(book.getDeletedAt()).isNull();
        }

        @Test
        void skipsSaveWhenNothingChanged() {
            BookEntity book = buildBook(10L);
            BookFileEntity bookFile = buildBookFile(100L, book, "test.epub", "hash123");
            bookFile.setFileSubPath("sub");
            book.setBookFiles(new ArrayList<>(List.of(bookFile)));

            when(entityManager.merge(any(LibraryPathEntity.class))).thenReturn(libraryPath);

            service.updatePathIfChanged(book, library, Path.of("/library/sub/test.epub"), "hash123");

            verify(jooqBookRepository, never()).updateLibrary(anyLong(), anyLong(), anyLong());
        }

        @Test
        void matchesFileByHash_notJustPrimary() {
            BookEntity book = buildBook(10L);
            BookFileEntity primaryFile = buildBookFile(100L, book, "primary.epub", "primaryhash");
            BookFileEntity secondaryFile = buildBookFile(101L, book, "secondary.pdf", "hash123");
            book.setBookFiles(new ArrayList<>(List.of(primaryFile, secondaryFile)));

            when(entityManager.merge(any(LibraryPathEntity.class))).thenReturn(libraryPath);
            fileUtilsMock.when(() -> FileUtils.getRelativeSubPath(anyString(), any(Path.class))).thenReturn("newsub");

            service.updatePathIfChanged(book, library, Path.of("/library/newsub/secondary.pdf"), "hash123");

            // Should update the secondary file, not primary
            assertThat(secondaryFile.getFileSubPath()).isEqualTo("newsub");
            assertThat(secondaryFile.getFileName()).isEqualTo("secondary.pdf");
            verify(jooqBookRepository).updateLibrary(10L, 1L, 1L);
            verify(bookAdditionalFileRepository).updateFileNameAndSubPath(101L, "secondary.pdf", "newsub");
            verify(jooqBookRepository).markUndeleted(List.of(10L));
        }

        @Test
        void sendsNotificationAfterUpdate() {
            BookEntity book = buildBook(10L);
            BookFileEntity bookFile = buildBookFile(100L, book, "test.epub", "hash123");
            book.setBookFiles(new ArrayList<>(List.of(bookFile)));

            when(entityManager.merge(any(LibraryPathEntity.class))).thenReturn(libraryPath);
            fileUtilsMock.when(() -> FileUtils.getRelativeSubPath(anyString(), any(Path.class))).thenReturn("newsub");

            service.updatePathIfChanged(book, library, Path.of("/library/newsub/test.epub"), "hash123");

            verify(notificationService).sendMessageToPermissions(any(), any(), any());
        }
    }

    @Nested
    class MarkAllBooksUnderPathAsDeleted {

        @Test
        void marksAllBooksAsDeleted() {
            BookEntity book1 = buildBook(10L);
            BookEntity book2 = buildBook(11L);
            when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(1L, "sub"))
                    .thenReturn(List.of(book1, book2));

            int count = service.markAllBooksUnderPathAsDeleted(1L, "sub");

            assertThat(count).isEqualTo(2);
            assertThat(book1.getDeleted()).isTrue();
            assertThat(book1.getDeletedAt()).isNotNull();
            assertThat(book2.getDeleted()).isTrue();
            verify(jooqBookRepository).markDeleted(List.of(10L, 11L), book1.getDeletedAt());
        }

        @Test
        void returnsZeroWhenNoBooksFound() {
            when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(1L, "empty"))
                    .thenReturn(List.of());

            int count = service.markAllBooksUnderPathAsDeleted(1L, "empty");

            assertThat(count).isZero();
        }

        @Test
        void throwsOnNullRelativePath() {
            assertThatThrownBy(() -> service.markAllBooksUnderPathAsDeleted(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class DelegationMethods {

        @Test
        void findByLibraryPathSubPathAndFileName_delegates() {
            when(bookRepository.findByLibraryPath_IdAndFileSubPathAndFileName(1L, "sub", "test.epub"))
                    .thenReturn(Optional.of(buildBook(10L)));

            Optional<BookEntity> result = service.findByLibraryPathSubPathAndFileName(1L, "sub", "test.epub");

            assertThat(result).isPresent();
        }

        @Test
        void findBookFileByLibraryPathSubPathAndFileName_delegates() {
            BookFileEntity bookFile = buildBookFile(100L, buildBook(10L), "test.epub", "hash");
            when(bookFileRepository.findByLibraryPathIdAndFileSubPathAndFileName(1L, "sub", "test.epub"))
                    .thenReturn(Optional.of(bookFile));

            Optional<BookFileEntity> result = service.findBookFileByLibraryPathSubPathAndFileName(1L, "sub", "test.epub");

            assertThat(result).isPresent();
            assertThat(result.get().getFileName()).isEqualTo("test.epub");
        }

        @Test
        void deleteBookFile_delegates() {
            BookFileEntity bookFile = buildBookFile(100L, buildBook(10L), "test.epub", "hash");

            service.deleteBookFile(bookFile);

            verify(jooqBookRepository).deleteBookFileById(100L);
        }

        @Test
        void markBookAsDeleted_setsDeletedFields() {
            BookEntity book = buildBook(10L);

            service.markBookAsDeleted(book);

            assertThat(book.getDeleted()).isTrue();
            assertThat(book.getDeletedAt()).isNotNull();
            verify(jooqBookRepository).markDeleted(List.of(10L), book.getDeletedAt());
        }

        @Test
        void countBookFilesByBookId_delegates() {
            when(bookFileRepository.countByBookId(10L)).thenReturn(3L);

            long count = service.countBookFilesByBookId(10L);

            assertThat(count).isEqualTo(3L);
        }
    }

    @Nested
    class RecoverFolderBook {

        @Test
        void recoversBookAndUpdatesFiles() {
            BookEntity book = buildBook(10L);
            book.setDeleted(true);
            BookFileEntity bookFile = buildBookFile(100L, book, "old.epub", "filehash1");
            book.setBookFiles(new ArrayList<>(List.of(bookFile)));

            var fileSnap = new FileSnapshot(100L, "old.epub", "sub", "filehash1", false, BookFileType.EPUB);
            var bookSnap = new BookSnapshot(10L, 1L, "sub", List.of(fileSnap));

            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(bookFileRepository.findById(100L)).thenReturn(Optional.of(bookFile));
            when(entityManager.merge(any(LibraryPathEntity.class))).thenReturn(libraryPath);
            fileUtilsMock.when(() -> FileUtils.getRelativeSubPath(anyString(), any(Path.class))).thenReturn("newsub");

            Map<Path, String> fileHashes = Map.of(Path.of("/library/newsub/new.epub"), "filehash1");

            service.recoverFolderBook(bookSnap, libraryPath, Path.of("/library/newsub"), fileHashes);

            assertThat(book.getDeleted()).isFalse();
            assertThat(book.getDeletedAt()).isNull();
            assertThat(bookFile.getFileName()).isEqualTo("new.epub");
            assertThat(bookFile.getFileSubPath()).isEqualTo("newsub");
            verify(bookAdditionalFileRepository).updateFileNameAndSubPath(100L, "new.epub", "newsub");
            verify(jooqBookRepository).updateBookFileHash(100L, "filehash1");
            verify(jooqBookRepository).updateLibrary(10L, 1L, 1L);
            verify(jooqBookRepository).markUndeleted(List.of(10L));
            verify(notificationService).sendMessageToPermissions(any(), any(), any());
        }

        @Test
        void bookNotFound_doesNothing() {
            var bookSnap = new BookSnapshot(999L, 1L, "sub", List.of());
            when(bookRepository.findById(999L)).thenReturn(Optional.empty());

            service.recoverFolderBook(bookSnap, libraryPath, Path.of("/library"), Map.of());

            verify(jooqBookRepository, never()).updateLibrary(anyLong(), anyLong(), anyLong());
        }

        @Test
        void multipleFilesRecovered() {
            BookEntity book = buildBook(10L);
            book.setDeleted(true);
            BookFileEntity file1 = buildBookFile(100L, book, "a.epub", "hash1");
            BookFileEntity file2 = buildBookFile(101L, book, "b.epub", "hash2");
            book.setBookFiles(new ArrayList<>(List.of(file1, file2)));

            var snap1 = new FileSnapshot(100L, "a.epub", "sub", "hash1", false, BookFileType.EPUB);
            var snap2 = new FileSnapshot(101L, "b.epub", "sub", "hash2", false, BookFileType.EPUB);
            var bookSnap = new BookSnapshot(10L, 1L, "sub", List.of(snap1, snap2));

            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(bookFileRepository.findById(100L)).thenReturn(Optional.of(file1));
            when(bookFileRepository.findById(101L)).thenReturn(Optional.of(file2));
            when(entityManager.merge(any(LibraryPathEntity.class))).thenReturn(libraryPath);
            fileUtilsMock.when(() -> FileUtils.getRelativeSubPath(eq("/library"), eq(Path.of("/new/a.epub")))).thenReturn("newsub1");
            fileUtilsMock.when(() -> FileUtils.getRelativeSubPath(eq("/library"), eq(Path.of("/new/b.epub")))).thenReturn("newsub2");

            Map<Path, String> fileHashes = Map.of(
                    Path.of("/new/a.epub"), "hash1",
                    Path.of("/new/b.epub"), "hash2");

            service.recoverFolderBook(bookSnap, libraryPath, Path.of("/new"), fileHashes);

            assertThat(book.getDeleted()).isFalse();
            verify(bookAdditionalFileRepository).updateFileNameAndSubPath(100L, "a.epub", "newsub1");
            verify(jooqBookRepository).updateBookFileHash(100L, "hash1");
            verify(bookAdditionalFileRepository).updateFileNameAndSubPath(101L, "b.epub", "newsub2");
            verify(jooqBookRepository).updateBookFileHash(101L, "hash2");
            verify(jooqBookRepository).updateLibrary(10L, 1L, 1L);
            verify(jooqBookRepository).markUndeleted(List.of(10L));
        }

        @Test
        void fileSnapshotWithNoHashMatch_leftUnchanged() {
            BookEntity book = buildBook(10L);
            BookFileEntity bookFile = buildBookFile(100L, book, "old.epub", "oldhash");
            book.setBookFiles(new ArrayList<>(List.of(bookFile)));

            var fileSnap = new FileSnapshot(100L, "old.epub", "sub", "nomatch", false, BookFileType.EPUB);
            var bookSnap = new BookSnapshot(10L, 1L, "sub", List.of(fileSnap));

            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(bookFileRepository.findById(100L)).thenReturn(Optional.of(bookFile));
            when(entityManager.merge(any(LibraryPathEntity.class))).thenReturn(libraryPath);

            Map<Path, String> fileHashes = Map.of(Path.of("/new/other.epub"), "differenthash");

            service.recoverFolderBook(bookSnap, libraryPath, Path.of("/new"), fileHashes);

            // File name should remain unchanged since hash didn't match
            assertThat(bookFile.getFileName()).isEqualTo("old.epub");
            verify(jooqBookRepository).updateLibrary(10L, 1L, 1L);
            verify(jooqBookRepository).markUndeleted(List.of(10L));
            verify(bookAdditionalFileRepository, never()).updateFileNameAndSubPath(anyLong(), any(), any());
            verify(jooqBookRepository, never()).updateBookFileHash(anyLong(), any());
        }
    }
}
