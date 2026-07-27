package org.booklore.service;

import org.booklore.config.BookmarkProperties;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookMark;
import org.booklore.model.dto.CreateBookMarkRequest;
import org.booklore.model.dto.UpdateBookMarkRequest;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqBookMarkRepository;
import org.booklore.service.book.BookMarkService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookMarkServiceTest {

    @Mock
    private JooqBookMarkRepository bookMarkRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private BookmarkProperties bookmarkProperties;

    @InjectMocks
    private BookMarkService bookMarkService;

    private final Long userId = 1L;
    private final Long bookId = 100L;
    private final Long bookmarkId = 50L;
    private BookLoreUser userDto;
    private BookMark bookmarkDto;

    @BeforeEach
    void setUp() {
        userDto = BookLoreUser.builder().id(userId).isDefaultPassword(false).build();
        bookmarkDto = BookMark.builder().id(bookmarkId).userId(userId).bookId(bookId).cfi("cfi").title("title").build();
    }

    @Test
    void getBookmarksForBook_Success() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByBookIdAndUserIdOrderByPriorityAscCreatedAtDesc(bookId, userId)).thenReturn(List.of(bookmarkDto));

        List<BookMark> result = bookMarkService.getBookmarksForBook(bookId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(bookmarkId, result.getFirst().getId());
        verify(bookMarkRepository).findByBookIdAndUserIdOrderByPriorityAscCreatedAtDesc(bookId, userId);
    }

    @Test
    void createBookmark_Success() {
        CreateBookMarkRequest request = CreateBookMarkRequest.builder()
                .bookId(bookId)
                .cfi("new-cfi")
                .title("New Bookmark")
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookmarkProperties.getDefaultPriority()).thenReturn(3);
        when(bookMarkRepository.existsByCfiAndBookIdAndUserId(eq("new-cfi"), eq(bookId), eq(userId), isNull())).thenReturn(false);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(bookRepository.existsById(bookId)).thenReturn(true);
        when(bookMarkRepository.insert(bookId, userId, "new-cfi", null, null, "New Bookmark", 3)).thenReturn(bookmarkDto);

        BookMark result = bookMarkService.createBookmark(request);

        assertNotNull(result);
        assertEquals(bookmarkId, result.getId());
        verify(bookMarkRepository).insert(bookId, userId, "new-cfi", null, null, "New Bookmark", 3);
    }

    @Test
    void createBookmark_Duplicate() {
        CreateBookMarkRequest request = CreateBookMarkRequest.builder()
                .bookId(bookId)
                .cfi("new-cfi")
                .title("New Bookmark")
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.existsByCfiAndBookIdAndUserId(eq("new-cfi"), eq(bookId), eq(userId), isNull())).thenReturn(true);

        assertThrows(APIException.class, () -> bookMarkService.createBookmark(request));
        verify(bookMarkRepository, never()).insert(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void createBookmark_BookNotFound() {
        CreateBookMarkRequest request = CreateBookMarkRequest.builder()
                .bookId(bookId)
                .cfi("new-cfi")
                .title("New Bookmark")
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.existsByCfiAndBookIdAndUserId(eq("new-cfi"), eq(bookId), eq(userId), isNull())).thenReturn(false);
        when(bookRepository.existsById(bookId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> bookMarkService.createBookmark(request));
        verify(bookMarkRepository, never()).insert(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteBookmark_Success() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(bookmarkDto);

        bookMarkService.deleteBookmark(bookmarkId);

        verify(bookMarkRepository).deleteById(bookmarkId);
    }

    @Test
    void deleteBookmark_NotFound() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(null);

        assertThrows(EntityNotFoundException.class, () -> bookMarkService.deleteBookmark(bookmarkId));
        verify(bookMarkRepository, never()).deleteById(anyLong());
    }

    @Test
    void updateBookmark_Success() {
        var updateRequest = UpdateBookMarkRequest.builder()
                .title("Updated Title")
                .color("#FF0000")
                .notes("Updated notes")
                .priority(3)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(bookmarkDto);
        when(bookMarkRepository.update(any(BookMark.class))).thenAnswer(inv -> inv.getArgument(0));

        BookMark result = bookMarkService.updateBookmark(bookmarkId, updateRequest);

        assertNotNull(result);
        assertEquals(bookmarkId, result.getId());
        assertEquals("Updated Title", result.getTitle());
        assertEquals("#FF0000", result.getColor());
        assertEquals("Updated notes", result.getNotes());
        assertEquals(3, result.getPriority());
        verify(bookMarkRepository).update(any(BookMark.class));
    }

    @Test
    void updateBookmark_NotFound() {
        var updateRequest = UpdateBookMarkRequest.builder()
                .title("Updated Title")
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(null);

        assertThrows(EntityNotFoundException.class, () -> bookMarkService.updateBookmark(bookmarkId, updateRequest));
        verify(bookMarkRepository, never()).update(any());
    }

    @Test
    void getBookmarkById_Success() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(bookmarkDto);

        BookMark result = bookMarkService.getBookmarkById(bookmarkId);

        assertNotNull(result);
        assertEquals(bookmarkId, result.getId());
        verify(bookMarkRepository).findByIdAndUserId(bookmarkId, userId);
    }

    @Test
    void getBookmarkById_NotFound() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(null);

        assertThrows(EntityNotFoundException.class, () -> bookMarkService.getBookmarkById(bookmarkId));
        verify(bookMarkRepository).findByIdAndUserId(bookmarkId, userId);
    }
}
