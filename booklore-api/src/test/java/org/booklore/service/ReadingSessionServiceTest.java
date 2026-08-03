package org.booklore.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqReadingSessionRepository;
import org.booklore.repository.jooq.JooqUserBookProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadingSessionServiceTest {

    @Mock AuthenticationService authenticationService;
    @Mock JooqReadingSessionRepository jooqReadingSessionRepository;
    @Mock JooqUserBookProgressRepository jooqUserBookProgressRepository;
    @Mock BookRepository bookRepository;
    @Mock UserRepository userRepository;
    @Mock UserBookProgressRepository userBookProgressRepository;

    @InjectMocks
    ReadingSessionService service;

    @BeforeEach
    void auth() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(user.getId()).thenReturn(42L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    private ReadingSessionRequest request() {
        return new ReadingSessionRequest(
                7L, BookFileType.EPUB,
                Instant.ofEpochSecond(1000), Instant.ofEpochSecond(4600),
                3600, "1h", 10.0f, 55.5f, 45.5f, "cfi/start", "cfi/end");
    }

    @Test
    void recordSession_insertsWithMappedArgs() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(new BookLoreUserEntity()));
        BookEntity book = new BookEntity();
        book.setId(7L);
        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));
        when(jooqReadingSessionRepository.insert(
                anyLong(), anyLong(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn(99L);

        service.recordSession(request());

        verify(jooqReadingSessionRepository).insert(
                eq(42L), eq(7L), eq(BookFileType.EPUB),
                eq(Instant.ofEpochSecond(1000)), eq(Instant.ofEpochSecond(4600)),
                eq(3600), eq("1h"), eq(10.0f), eq(55.5f), eq(45.5f), eq("cfi/start"), eq("cfi/end"));
    }

    @Test
    void recordSession_userNotFound_throws() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.recordSession(request()));
        verify(jooqReadingSessionRepository, never()).insert(
                anyLong(), anyLong(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void recordSession_bookNotFound_throws() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(new BookLoreUserEntity()));
        when(bookRepository.findById(7L)).thenReturn(Optional.empty());
        assertThrows(APIException.class, () -> service.recordSession(request()));
        verify(jooqReadingSessionRepository, never()).insert(
                anyLong(), anyLong(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any());
    }
}
