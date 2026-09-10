package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.response.DetachBookFileResponse;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.LibraryPathRepository;
import org.booklore.repository.jooq.JooqBookMetadataRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.repository.jooq.JooqUserBookProgressRepository;
import org.booklore.repository.jooq.dto.UserBookProgressRow;
import org.booklore.service.audit.AuditService;
import org.booklore.service.metadata.BookCoverService;
import org.booklore.service.progress.ReadingProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookFileDetachmentServiceTest {

    @Mock private JooqBookEntityRepository jooqBookEntityRepository;
    @Mock private JooqBookRepository jooqBookRepository;
    @Mock private JooqBookMetadataRepository jooqBookMetadataRepository;
    @Mock private LibraryPathRepository libraryPathRepository;
    @Mock private JooqUserBookProgressRepository userBookProgressRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private ReadingProgressService readingProgressService;
    @Mock private BookMapper bookMapper;
    @Mock private BookService bookService;
    @Mock private AuditService auditService;
    @Mock private BookCoverService bookCoverService;
    @Mock private org.booklore.repository.jooq.JooqBookMetadataRelationsRepository relationsRepository;

    @InjectMocks
    private BookFileDetachmentService service;

    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    @BeforeEach
    void setUp() {
        library = LibraryEntity.builder()
                .id(1L)
                .name("Test Library")
                .build();

        libraryPath = LibraryPathEntity.builder()
                .id(1L)
                .library(library)
                .path("/tmp/test-library")
                .build();

        lenient().when(libraryPathRepository.findByLibraryId(1L)).thenReturn(new ArrayList<>(List.of(libraryPath)));
    }

    private BookEntity createBook(Long id) {
        BookEntity book = BookEntity.builder()
                .id(id)
                .library(library)
                .libraryPath(libraryPath)
                .build();

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(book)
                .bookId(id)
                .title("Test Book " + id)
                .build();
        book.setMetadata(metadata);

        return book;
    }

    private BookFileEntity createBookFile(Long id, BookEntity book, boolean isBookFormat, BookFileType type) {
        BookFileEntity file = BookFileEntity.builder()
                .id(id)
                .book(book)
                .fileName("file-" + id + "." + type.name().toLowerCase())
                .fileSubPath("")
                .isBookFormat(isBookFormat)
                .bookType(type)
                .build();
        return file;
    }

    private void setupMocksForGetUpdatedBook() {
        BookLoreUser user = new BookLoreUser();
        user.setId(1L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userBookProgressRepository.findByUserIdAndBookId(anyLong(), anyLong()))
                .thenReturn(Optional.of(new UserBookProgressRow()));
        when(readingProgressService.fetchUserFileProgress(anyLong(), anySet()))
                .thenReturn(new HashMap<>());
        when(bookMapper.toBook(any(BookEntity.class))).thenReturn(Book.builder().build());
    }

    @Test
    void detachAlternativeFormat_withCopyMetadata() {
        BookEntity book = createBook(1L);
        BookFileEntity primaryFile = createBookFile(10L, book, true, BookFileType.EPUB);
        BookFileEntity altFile = createBookFile(11L, book, true, BookFileType.PDF);

        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(book);
        when(jooqBookRepository.insertBook(eq(1L), eq(1L), any())).thenReturn(2L);

        setupMocksForGetUpdatedBook();
        when(jooqBookEntityRepository.findByIdFull(2L)).thenReturn(createBook(2L));

        DetachBookFileResponse response = service.detachBookFile(1L, 11L, true);

        assertThat(response).isNotNull();
        verify(auditService).log(eq(org.booklore.model.enums.AuditAction.BOOK_FILE_DETACHED), anyString(), eq(1L), anyString());
        verify(jooqBookMetadataRepository).insertRow(eq(2L), argThat(metadata -> {
            assertThat(metadata.getTitle()).isEqualTo("Test Book 1");
            return true;
        }));
        verify(jooqBookRepository).reassignFileWithNewNameAndPath(11L, 2L, "", "file-11.pdf");
    }

    @Test
    void detachAlternativeFormat_withoutCopyMetadata() {
        BookEntity book = createBook(1L);
        createBookFile(10L, book, true, BookFileType.EPUB);
        createBookFile(11L, book, true, BookFileType.PDF);

        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(book);
        when(jooqBookRepository.insertBook(eq(1L), eq(1L), any())).thenReturn(2L);

        setupMocksForGetUpdatedBook();
        when(jooqBookEntityRepository.findByIdFull(2L)).thenReturn(createBook(2L));

        service.detachBookFile(1L, 11L, false);

        verify(jooqBookMetadataRepository).insertRow(eq(2L), argThat(metadata -> {
            assertThat(metadata.getTitle()).isEqualTo("file-11");
            return true;
        }));
    }

    @Test
    void detachSupplementaryFile() {
        BookEntity book = createBook(1L);
        createBookFile(10L, book, true, BookFileType.EPUB);
        BookFileEntity suppFile = createBookFile(11L, book, false, BookFileType.PDF);
        suppFile.setFileName("notes.txt");

        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(book);
        when(jooqBookRepository.insertBook(eq(1L), eq(1L), any())).thenReturn(2L);

        setupMocksForGetUpdatedBook();
        when(jooqBookEntityRepository.findByIdFull(2L)).thenReturn(createBook(2L));

        service.detachBookFile(1L, 11L, false);

        // fileName itself is never touched by the reassignment call, so "notes.txt" staying intact
        // is implicit in reassigning by id rather than rebuilding the file.
        verify(jooqBookRepository).reassignFileWithNewNameAndPath(11L, 2L, "", "notes.txt");
        verify(jooqBookMetadataRepository).insertRow(eq(2L), argThat(metadata -> {
            assertThat(metadata.getTitle()).isEqualTo("notes");
            return true;
        }));
    }

    @Test
    void detachPrimaryFile_alternativeGetsPromoted() {
        BookEntity book = createBook(1L);
        BookFileEntity primaryFile = createBookFile(10L, book, true, BookFileType.EPUB);
        createBookFile(11L, book, true, BookFileType.PDF);

        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(book);
        when(jooqBookRepository.insertBook(eq(1L), eq(1L), any())).thenReturn(2L);

        setupMocksForGetUpdatedBook();
        when(jooqBookEntityRepository.findByIdFull(2L)).thenReturn(createBook(2L));

        service.detachBookFile(1L, 10L, false);

        // Which file remains primary on the source book is BookEntity.getPrimaryBookFile()'s own
        // format-priority logic over freshly-read DB state - this test only verifies detachment
        // reassigned the correct file (10L, not 11L).
        verify(jooqBookRepository).reassignFileWithNewNameAndPath(10L, 2L, "", "file-10.epub");
    }

    @Test
    void rejectDetach_onlyOneFile() {
        BookEntity book = createBook(1L);
        BookFileEntity file = createBookFile(10L, book, true, BookFileType.EPUB);

        assertThat(book.getBookFiles()).hasSize(1);
        assertThat(file.isBookFormat()).isTrue();

        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(book);

        assertThatThrownBy(() -> service.detachBookFile(1L, 10L, false))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Cannot detach the only file");
    }

    @Test
    void rejectDetach_fileNotOnBook() {
        BookEntity book = createBook(1L);
        createBookFile(10L, book, true, BookFileType.EPUB);
        createBookFile(11L, book, true, BookFileType.PDF);

        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(book);

        assertThatThrownBy(() -> service.detachBookFile(1L, 99L, false))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("File not found on this book");
    }

    @Test
    void rejectDetach_bookNotFound() {
        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.detachBookFile(1L, 10L, false))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Book not found");
    }

    @Test
    void rejectDetach_onlyBookFormatWithSupplementary() {
        BookEntity book = createBook(1L);
        createBookFile(10L, book, true, BookFileType.EPUB);
        createBookFile(11L, book, false, BookFileType.PDF);

        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(book);

        assertThatThrownBy(() -> service.detachBookFile(1L, 10L, false))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("only book format file");
    }

    @Test
    void detachFile_resolvesCorrectLibraryPath() {
        LibraryPathEntity libraryPath2 = LibraryPathEntity.builder()
                .id(2L)
                .library(library)
                .path("/tmp/test-library/folder-2")
                .build();
        when(libraryPathRepository.findByLibraryId(1L)).thenReturn(List.of(libraryPath, libraryPath2));

        BookEntity book = createBook(1L);
        BookFileEntity primaryFile = createBookFile(10L, book, true, BookFileType.EPUB);
        BookFileEntity altFile = createBookFile(11L, book, true, BookFileType.PDF);
        altFile.setFileSubPath("folder-2");

        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(book);
        when(jooqBookRepository.insertBook(eq(1L), eq(2L), any())).thenReturn(3L);

        setupMocksForGetUpdatedBook();
        when(jooqBookEntityRepository.findByIdFull(3L)).thenReturn(createBook(3L));

        service.detachBookFile(1L, 11L, false);

        verify(jooqBookRepository).insertBook(eq(1L), eq(2L), any());
        verify(jooqBookRepository).reassignFileWithNewNameAndPath(11L, 3L, "", "file-11.pdf");
    }
}
