package org.booklore.service.library;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.service.NotificationService;
import org.booklore.util.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookDeletionServiceTest {

    @Mock private JooqBookEntityRepository jooqBookEntityRepository;
    @Mock private JooqBookRepository jooqBookRepository;
    @Mock private FileService fileService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private BookDeletionService bookDeletionService;

    @TempDir
    Path tempDir;

    @Test
    void deleteRemovedAdditionalFiles_emptyList_doesNothing() {
        bookDeletionService.deleteRemovedAdditionalFiles(List.of());

        verifyNoInteractions(jooqBookRepository);
    }

    @Test
    void deleteRemovedAdditionalFiles_nonEmpty_deletesViaJooq() {
        bookDeletionService.deleteRemovedAdditionalFiles(List.of(1L, 2L));

        verify(jooqBookRepository).deleteBookFilesByIds(List.of(1L, 2L));
    }

    @Test
    void deleteRemovedBooks_cleansUpFilesDeletesViaJooqAndNotifies() {
        when(fileService.getImagesFolder(1L)).thenReturn(tempDir.resolve("images-1").toString());
        when(fileService.getBookMetadataBackupPath(1L)).thenReturn(tempDir.resolve("backup-1").toString());

        bookDeletionService.deleteRemovedBooks(List.of(1L));

        verify(jooqBookRepository).deleteByIds(List.of(1L));
        verify(notificationService).sendMessage(Topic.BOOKS_REMOVE, List.of(1L));
        verifyNoInteractions(jooqBookEntityRepository);
    }

    @Test
    void processDeletedLibraryFiles_emptyList_doesNothing() {
        bookDeletionService.processDeletedLibraryFiles(List.of(), List.of());

        verifyNoInteractions(jooqBookEntityRepository, jooqBookRepository);
    }

    @Test
    void processDeletedLibraryFiles_bookHasRemainingFiles_promotesInsteadOfDeleting() {
        BookFileEntity keptFile = BookFileEntity.builder().id(10L).fileName("book.pdf").isBookFormat(true).build();
        BookFileEntity removedFile = BookFileEntity.builder().id(11L).fileName("book.epub").isBookFormat(true).build();
        BookEntity book = BookEntity.builder().id(1L).bookFiles(new ArrayList<>(List.of(keptFile, removedFile))).build();
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(1L))).thenReturn(List.of(book));

        LibraryFile keptLibraryFile = LibraryFile.builder().fileName("book.pdf").build();

        bookDeletionService.processDeletedLibraryFiles(List.of(1L), List.of(keptLibraryFile));

        verify(jooqBookRepository).deleteBookFilesByIds(List.of(11L));
        assertThat(book.getBookFiles()).containsExactly(keptFile);
        verify(jooqBookRepository, never()).deleteByIds(any());
    }

    @Test
    void processDeletedLibraryFiles_bookHasNoRemainingFiles_deletesBook() {
        BookFileEntity removedFile = BookFileEntity.builder().id(11L).fileName("book.epub").isBookFormat(true).build();
        BookEntity book = BookEntity.builder().id(1L).bookFiles(new ArrayList<>(List.of(removedFile))).build();
        when(jooqBookEntityRepository.findAllByIdsIncludingDeleted(Set.of(1L))).thenReturn(List.of(book));
        when(fileService.getImagesFolder(1L)).thenReturn(tempDir.resolve("images-1").toString());
        when(fileService.getBookMetadataBackupPath(1L)).thenReturn(tempDir.resolve("backup-1").toString());

        bookDeletionService.processDeletedLibraryFiles(List.of(1L), List.of());

        verify(jooqBookRepository).deleteBookFilesByIds(List.of(11L));
        verify(jooqBookRepository).deleteByIds(List.of(1L));
        verify(notificationService).sendMessage(Topic.BOOKS_REMOVE, List.of(1L));
    }

    @Test
    void purgeDisallowedFormats_noAllowedFormats_doesNothing() {
        LibraryEntity library = LibraryEntity.builder().id(1L).name("Lib").allowedFormats(List.of()).build();

        bookDeletionService.purgeDisallowedFormats(library);

        verifyNoInteractions(jooqBookRepository);
    }

    @Test
    void purgeDisallowedFormats_removesDisallowedFilesAndBooksWithNoneRemaining() {
        LibraryEntity library = LibraryEntity.builder().id(1L).name("Lib").allowedFormats(List.of(BookFileType.EPUB)).build();

        BookEntity bookA = BookEntity.builder().id(100L).build();
        BookEntity bookB = BookEntity.builder().id(200L).build();

        BookFileEntity bookAEpub = BookFileEntity.builder().id(1L).book(bookA).isBookFormat(true).bookType(BookFileType.EPUB).build();
        BookFileEntity bookACbx = BookFileEntity.builder().id(2L).book(bookA).isBookFormat(true).bookType(BookFileType.CBX).build();
        BookFileEntity bookBCbx = BookFileEntity.builder().id(3L).book(bookB).isBookFormat(true).bookType(BookFileType.CBX).build();

        when(jooqBookEntityRepository.findBookFilesByLibraryId(1L)).thenReturn(List.of(bookAEpub, bookACbx, bookBCbx));
        when(fileService.getImagesFolder(200L)).thenReturn(tempDir.resolve("images-200").toString());
        when(fileService.getBookMetadataBackupPath(200L)).thenReturn(tempDir.resolve("backup-200").toString());

        bookDeletionService.purgeDisallowedFormats(library);

        verify(jooqBookRepository).deleteBookFilesByIds(List.of(2L, 3L));
        verify(jooqBookRepository).deleteByIds(List.of(200L));
        verify(jooqBookRepository, never()).deleteByIds(List.of(100L));
    }
}
