package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookNote;
import org.booklore.model.dto.CreateBookNoteRequest;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqBookNoteRepository;
import org.booklore.service.book.BookNoteService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookNoteServiceTest {

    private JooqBookNoteRepository bookNoteRepository;
    private BookRepository bookRepository;
    private UserRepository userRepository;
    private BookNoteService service;

    private final Long userId = 1L;
    private final Long bookId = 2L;
    private final Long noteId = 3L;

    @BeforeEach
    void setUp() {
        bookNoteRepository = mock(JooqBookNoteRepository.class);
        bookRepository = mock(BookRepository.class);
        userRepository = mock(UserRepository.class);
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        service = new BookNoteService(bookNoteRepository, bookRepository, userRepository, authenticationService);

        BookLoreUser user = new BookLoreUser();
        user.setId(userId);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    @Test
    void getNotesForBook_returnsNotesFromRepository() {
        BookNote dto = BookNote.builder().id(noteId).build();
        when(bookNoteRepository.findByBookIdAndUserIdOrderByUpdatedAtDesc(bookId, userId))
                .thenReturn(Collections.singletonList(dto));

        List<BookNote> result = service.getNotesForBook(bookId);

        assertEquals(1, result.size());
        assertEquals(noteId, result.getFirst().getId());
    }

    @Test
    void createOrUpdateNote_createsNewNote_whenIdIsNull() {
        CreateBookNoteRequest req = CreateBookNoteRequest.builder()
                .bookId(bookId)
                .title("t")
                .content("c")
                .build();

        BookNote dto = BookNote.builder().id(noteId).title("t").content("c").build();
        when(bookRepository.existsById(bookId)).thenReturn(true);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(bookNoteRepository.insert(bookId, userId, "t", "c")).thenReturn(dto);

        BookNote result = service.createOrUpdateNote(req);

        assertEquals(noteId, result.getId());
        verify(bookNoteRepository).insert(bookId, userId, "t", "c");
        verify(bookNoteRepository, never()).update(anyLong(), anyLong(), any(), any());
    }

    @Test
    void createOrUpdateNote_updatesExistingNote_whenIdIsPresent() {
        CreateBookNoteRequest req = CreateBookNoteRequest.builder()
                .id(noteId)
                .bookId(bookId)
                .title("new title")
                .content("new content")
                .build();

        BookNote dto = BookNote.builder().id(noteId).title("new title").content("new content").build();
        when(bookRepository.existsById(bookId)).thenReturn(true);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(bookNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(BookNote.builder().id(noteId).build());
        when(bookNoteRepository.update(noteId, userId, "new title", "new content")).thenReturn(dto);

        BookNote result = service.createOrUpdateNote(req);

        assertEquals("new title", result.getTitle());
        assertEquals("new content", result.getContent());
        verify(bookNoteRepository).update(noteId, userId, "new title", "new content");
        verify(bookNoteRepository, never()).insert(anyLong(), anyLong(), any(), any());
    }

    @Test
    void createOrUpdateNote_throwsIfBookNotFound() {
        CreateBookNoteRequest req = CreateBookNoteRequest.builder()
                .bookId(bookId)
                .title("t")
                .content("c")
                .build();

        when(bookRepository.existsById(bookId)).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createOrUpdateNote(req));

        assertTrue(
                ex.getMessage().contains("BOOK_NOT_FOUND") || ex.getMessage().contains(String.valueOf(bookId)),
                "Exception message should contain 'BOOK_NOT_FOUND' or the book id"
        );
    }

    @Test
    void createOrUpdateNote_throwsIfUserNotFound() {
        CreateBookNoteRequest req = CreateBookNoteRequest.builder()
                .bookId(bookId)
                .title("t")
                .content("c")
                .build();

        when(bookRepository.existsById(bookId)).thenReturn(true);
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.createOrUpdateNote(req));
    }

    @Test
    void createOrUpdateNote_throwsIfNoteNotFoundForUpdate() {
        CreateBookNoteRequest req = CreateBookNoteRequest.builder()
                .id(noteId)
                .bookId(bookId)
                .title("t")
                .content("c")
                .build();

        when(bookRepository.existsById(bookId)).thenReturn(true);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(bookNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(null);

        assertThrows(EntityNotFoundException.class, () -> service.createOrUpdateNote(req));
    }

    @Test
    void deleteNote_deletesIfExists() {
        when(bookNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(BookNote.builder().id(noteId).build());

        service.deleteNote(noteId);

        verify(bookNoteRepository).deleteById(noteId);
    }

    @Test
    void deleteNote_throwsIfNotFound() {
        when(bookNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(null);
        assertThrows(EntityNotFoundException.class, () -> service.deleteNote(noteId));
    }
}
