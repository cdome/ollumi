package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookNoteV2;
import org.booklore.model.dto.CreateBookNoteV2Request;
import org.booklore.model.dto.UpdateBookNoteV2Request;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqBookNoteV2Repository;
import org.booklore.service.book.BookNoteV2Service;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookNoteV2ServiceTest {

    private JooqBookNoteV2Repository bookNoteV2Repository;
    private BookRepository bookRepository;
    private UserRepository userRepository;
    private AuthenticationService authenticationService;
    private BookNoteV2Service service;

    private final Long userId = 1L;
    private final Long bookId = 2L;
    private final Long noteId = 3L;
    private final String cfi = "epubcfi(/6/4!/4/2/1:0)";
    private final String selectedText = "Some selected text";
    private final String noteContent = "My note content";
    private final String defaultColor = "#FFC107";
    private final String customColor = "#FF5733";
    private final String chapterTitle = "Chapter 1";

    @BeforeEach
    void setUp() {
        bookNoteV2Repository = mock(JooqBookNoteV2Repository.class);
        bookRepository = mock(BookRepository.class);
        userRepository = mock(UserRepository.class);
        authenticationService = mock(AuthenticationService.class);
        service = new BookNoteV2Service(bookNoteV2Repository, bookRepository, userRepository, authenticationService);

        BookLoreUser user = new BookLoreUser();
        user.setId(userId);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    @Test
    void getNotesForBook_returnsNotesFromRepository() {
        BookNoteV2 dto = BookNoteV2.builder().id(noteId).cfi(cfi).build();
        when(bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId))
                .thenReturn(Collections.singletonList(dto));

        List<BookNoteV2> result = service.getNotesForBook(bookId);

        assertEquals(1, result.size());
        assertEquals(noteId, result.getFirst().getId());
        assertEquals(cfi, result.getFirst().getCfi());
        verify(bookNoteV2Repository).findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId);
    }

    @Test
    void getNotesForBook_returnsEmptyList_whenNoNotes() {
        when(bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId))
                .thenReturn(Collections.emptyList());

        List<BookNoteV2> result = service.getNotesForBook(bookId);

        assertTrue(result.isEmpty());
    }

    @Test
    void getNoteById_returnsNote_whenExists() {
        BookNoteV2 dto = BookNoteV2.builder().id(noteId).cfi(cfi).build();
        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(dto);

        BookNoteV2 result = service.getNoteById(noteId);

        assertEquals(noteId, result.getId());
        assertEquals(cfi, result.getCfi());
    }

    @Test
    void getNoteById_throwsEntityNotFoundException_whenNotFound() {
        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(null);

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.getNoteById(noteId));

        assertTrue(ex.getMessage().contains(String.valueOf(noteId)));
    }

    @Test
    void createNote_createsNewNote_withDefaultColor() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .selectedText(selectedText)
                .noteContent(noteContent)
                .chapterTitle(chapterTitle)
                .build();

        BookNoteV2 dto = BookNoteV2.builder().id(noteId).color(defaultColor).build();
        when(bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)).thenReturn(false);
        when(bookRepository.existsById(bookId)).thenReturn(true);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(bookNoteV2Repository.insert(bookId, userId, cfi, selectedText, noteContent, defaultColor, chapterTitle)).thenReturn(dto);

        BookNoteV2 result = service.createNote(request);

        assertEquals(noteId, result.getId());
        assertEquals(defaultColor, result.getColor());
        verify(bookNoteV2Repository).insert(bookId, userId, cfi, selectedText, noteContent, defaultColor, chapterTitle);
    }

    @Test
    void createNote_createsNewNote_withCustomColor() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .selectedText(selectedText)
                .noteContent(noteContent)
                .color(customColor)
                .chapterTitle(chapterTitle)
                .build();

        BookNoteV2 dto = BookNoteV2.builder().id(noteId).color(customColor).build();
        when(bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)).thenReturn(false);
        when(bookRepository.existsById(bookId)).thenReturn(true);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(bookNoteV2Repository.insert(bookId, userId, cfi, selectedText, noteContent, customColor, chapterTitle)).thenReturn(dto);

        BookNoteV2 result = service.createNote(request);

        assertEquals(customColor, result.getColor());
        verify(bookNoteV2Repository).insert(bookId, userId, cfi, selectedText, noteContent, customColor, chapterTitle);
    }

    @Test
    void createNote_throwsAPIException_whenDuplicateCfiExists() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .noteContent(noteContent)
                .build();

        when(bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)).thenReturn(true);

        APIException ex = assertThrows(APIException.class, () -> service.createNote(request));

        assertTrue(ex.getMessage().contains("Note already exists"));
        verify(bookNoteV2Repository, never()).insert(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void createNote_throwsEntityNotFoundException_whenBookNotFound() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .noteContent(noteContent)
                .build();

        when(bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)).thenReturn(false);
        when(bookRepository.existsById(bookId)).thenReturn(false);

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.createNote(request));

        assertTrue(ex.getMessage().contains(String.valueOf(bookId)));
        verify(bookNoteV2Repository, never()).insert(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void createNote_throwsEntityNotFoundException_whenUserNotFound() {
        CreateBookNoteV2Request request = CreateBookNoteV2Request.builder()
                .bookId(bookId)
                .cfi(cfi)
                .noteContent(noteContent)
                .build();

        when(bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)).thenReturn(false);
        when(bookRepository.existsById(bookId)).thenReturn(true);
        when(userRepository.existsById(userId)).thenReturn(false);

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.createNote(request));

        assertTrue(ex.getMessage().contains(String.valueOf(userId)));
        verify(bookNoteV2Repository, never()).insert(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void updateNote_updatesAllFields() {
        UpdateBookNoteV2Request request = UpdateBookNoteV2Request.builder()
                .noteContent("Updated content")
                .color("#00FF00")
                .chapterTitle("Updated chapter")
                .build();

        BookNoteV2 existing = BookNoteV2.builder()
                .id(noteId).cfi(cfi).noteContent(noteContent).color(defaultColor).chapterTitle(chapterTitle).build();

        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(existing);
        when(bookNoteV2Repository.update(any(BookNoteV2.class))).thenAnswer(inv -> inv.getArgument(0));

        BookNoteV2 result = service.updateNote(noteId, request);

        assertEquals("Updated content", result.getNoteContent());
        assertEquals("#00FF00", result.getColor());
        assertEquals("Updated chapter", result.getChapterTitle());

        ArgumentCaptor<BookNoteV2> captor = ArgumentCaptor.forClass(BookNoteV2.class);
        verify(bookNoteV2Repository).update(captor.capture());
        assertEquals("Updated content", captor.getValue().getNoteContent());
        assertEquals("#00FF00", captor.getValue().getColor());
        assertEquals("Updated chapter", captor.getValue().getChapterTitle());
    }

    @Test
    void updateNote_updatesOnlyNonNullFields() {
        UpdateBookNoteV2Request request = UpdateBookNoteV2Request.builder()
                .noteContent("Updated content")
                .build();

        BookNoteV2 existing = BookNoteV2.builder()
                .id(noteId).cfi(cfi).noteContent(noteContent).color(defaultColor).chapterTitle(chapterTitle).build();

        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(existing);
        when(bookNoteV2Repository.update(any(BookNoteV2.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateNote(noteId, request);

        ArgumentCaptor<BookNoteV2> captor = ArgumentCaptor.forClass(BookNoteV2.class);
        verify(bookNoteV2Repository).update(captor.capture());
        assertEquals("Updated content", captor.getValue().getNoteContent());
        assertEquals(defaultColor, captor.getValue().getColor());
        assertEquals(chapterTitle, captor.getValue().getChapterTitle());
    }

    @Test
    void updateNote_throwsEntityNotFoundException_whenNoteNotFound() {
        UpdateBookNoteV2Request request = UpdateBookNoteV2Request.builder()
                .noteContent("Updated content")
                .build();

        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(null);

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.updateNote(noteId, request));

        assertTrue(ex.getMessage().contains(String.valueOf(noteId)));
        verify(bookNoteV2Repository, never()).update(any());
    }

    @Test
    void deleteNote_deletesExistingNote() {
        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(BookNoteV2.builder().id(noteId).build());

        service.deleteNote(noteId);

        verify(bookNoteV2Repository).deleteById(noteId);
    }

    @Test
    void deleteNote_throwsEntityNotFoundException_whenNotFound() {
        when(bookNoteV2Repository.findByIdAndUserId(noteId, userId)).thenReturn(null);

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.deleteNote(noteId));

        assertTrue(ex.getMessage().contains(String.valueOf(noteId)));
        verify(bookNoteV2Repository, never()).deleteById(anyLong());
    }

    @Test
    void getNotesForBook_returnsMultipleNotes_orderedByCreatedAtDesc() {
        BookNoteV2 dto1 = BookNoteV2.builder().id(1L).cfi("cfi1").build();
        BookNoteV2 dto2 = BookNoteV2.builder().id(2L).cfi("cfi2").build();
        BookNoteV2 dto3 = BookNoteV2.builder().id(3L).cfi("cfi3").build();

        when(bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId))
                .thenReturn(List.of(dto3, dto2, dto1));

        List<BookNoteV2> result = service.getNotesForBook(bookId);

        assertEquals(3, result.size());
        assertEquals(3L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals(1L, result.get(2).getId());
    }
}
