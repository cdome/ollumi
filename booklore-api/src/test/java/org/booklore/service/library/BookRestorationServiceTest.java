package org.booklore.service.library;

import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookRestorationServiceTest {

    @Mock private JooqBookEntityRepository jooqBookEntityRepository;
    @Mock private JooqBookRepository jooqBookRepository;
    @Mock private BookMapper bookMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private BookRestorationService bookRestorationService;

    @Test
    void restoreDeletedBooks_emptyLibraryFiles_doesNothing() {
        bookRestorationService.restoreDeletedBooks(List.of());

        verifyNoInteractions(jooqBookEntityRepository, jooqBookRepository, notificationService);
    }

    @Test
    void restoreDeletedBooks_noDeletedBooksMatchCurrentPaths_doesNothing() {
        LibraryEntity library = LibraryEntity.builder().id(1L).name("Lib").build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/library");
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(library)
                .libraryPathEntity(libraryPath)
                .fileSubPath("books")
                .fileName("book.epub")
                .build();

        when(jooqBookEntityRepository.findDeletedByLibraryId(1L)).thenReturn(List.of());

        bookRestorationService.restoreDeletedBooks(List.of(libraryFile));

        verifyNoInteractions(jooqBookRepository, notificationService);
    }

    @Test
    void restoreDeletedBooks_matchingBook_restoresViaJooqAndNotifies() {
        LibraryEntity library = LibraryEntity.builder().id(1L).name("Lib").build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/library");
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(library)
                .libraryPathEntity(libraryPath)
                .fileSubPath("books")
                .fileName("book.epub")
                .build();

        BookFileEntity primaryFile = BookFileEntity.builder()
                .fileName("book.epub")
                .fileSubPath("books")
                .isBookFormat(true)
                .build();
        BookEntity deletedBook = BookEntity.builder()
                .id(5L)
                .libraryPath(libraryPath)
                .deleted(true)
                .bookFiles(new ArrayList<>(List.of(primaryFile)))
                .build();

        when(jooqBookEntityRepository.findDeletedByLibraryId(1L)).thenReturn(List.of(deletedBook));
        when(bookMapper.toBookWithDescription(deletedBook, false)).thenReturn(Book.builder().id(5L).build());

        bookRestorationService.restoreDeletedBooks(List.of(libraryFile));

        assertThat(deletedBook.getDeleted()).isFalse();
        assertThat(deletedBook.getDeletedAt()).isNull();
        assertThat(deletedBook.getAddedOn()).isNotNull();
        verify(jooqBookRepository).restoreDeleted(eq(List.of(5L)), eq(deletedBook.getAddedOn()));
        verify(notificationService).sendMessage(eq(Topic.BOOK_ADD), any());
    }

    @Test
    void restoreDeletedBooks_deletedBookWithNoFiles_isSkipped() {
        LibraryEntity library = LibraryEntity.builder().id(1L).name("Lib").build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/library");
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(library)
                .libraryPathEntity(libraryPath)
                .fileSubPath("books")
                .fileName("book.epub")
                .build();

        BookEntity fileless = BookEntity.builder().id(6L).libraryPath(libraryPath).deleted(true).bookFiles(new ArrayList<>()).build();
        when(jooqBookEntityRepository.findDeletedByLibraryId(1L)).thenReturn(List.of(fileless));

        bookRestorationService.restoreDeletedBooks(List.of(libraryFile));

        verifyNoInteractions(jooqBookRepository, notificationService);
    }
}
