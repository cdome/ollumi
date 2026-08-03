package org.booklore.service.progress;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.progress.EpubProgress;
import org.booklore.model.dto.progress.PdfProgress;
import org.booklore.model.dto.request.ReadProgressRequest;
import org.booklore.model.dto.response.BookStatusUpdateResponse;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.model.enums.ResetProgressType;
import org.booklore.repository.*;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.repository.jooq.JooqUserBookFileProgressRepository;
import org.booklore.repository.jooq.JooqUserBookProgressRepository;
import org.booklore.repository.jooq.dto.UserBookFileProgressRow;
import org.booklore.repository.jooq.dto.UserBookProgressRow;
import org.booklore.service.kobo.KoboReadingStateService;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadingProgressServiceTest {

    @Mock
    private JooqUserBookProgressRepository jooqUserBookProgressRepository;
    @Mock
    private JooqUserBookFileProgressRepository userBookFileProgressRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private JooqBookRepository jooqBookRepository;
    @Mock
    private BookFileRepository bookFileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private KoboReadingStateService koboReadingStateService;
    @Mock
    private HardcoverSyncService hardcoverSyncService;

    @InjectMocks
    private ReadingProgressService readingProgressService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void fetchUserProgress_shouldReturnProgressMap() {
        Long userId = 1L;
        Set<Long> bookIds = Set.of(1L, 2L);

        BookEntity book1 = new BookEntity();
        book1.setId(1L);
        BookEntity book2 = new BookEntity();
        book2.setId(2L);

        UserBookProgressRow progress1 = new UserBookProgressRow();
        progress1.setBookId(book1.getId());
        UserBookProgressRow progress2 = new UserBookProgressRow();
        progress2.setBookId(book2.getId());

        when(jooqUserBookProgressRepository.findByUserIdAndBookIdIn(userId, bookIds))
                .thenReturn(List.of(progress1, progress2));

        Map<Long, UserBookProgressRow> result = readingProgressService.fetchUserProgress(userId, bookIds);

        assertEquals(2, result.size());
        assertEquals(progress1, result.get(1L));
        assertEquals(progress2, result.get(2L));
    }

    @Test
    void fetchUserFileProgress_emptyBookIds_shouldReturnEmptyMap() {
        Map<Long, UserBookFileProgressRow> result =
                readingProgressService.fetchUserFileProgress(1L, Collections.emptySet());

        assertTrue(result.isEmpty());
        verify(userBookFileProgressRepository, never()).findByUserIdAndBookFileBookIdIn(anyLong(), anyCollection());
    }

    @Test
    void fetchUserFileProgress_shouldReturnMostRecentPerBook() {
        Long userId = 1L;
        Set<Long> bookIds = Set.of(1L);

        UserBookFileProgressRow progress1 = new UserBookFileProgressRow();
        progress1.setBookId(1L);
        progress1.setLastReadTime(Instant.now().minusSeconds(100));

        UserBookFileProgressRow progress2 = new UserBookFileProgressRow();
        progress2.setBookId(1L);
        progress2.setLastReadTime(Instant.now());

        when(userBookFileProgressRepository.findByUserIdAndBookFileBookIdIn(userId, bookIds))
                .thenReturn(List.of(progress1, progress2));

        Map<Long, UserBookFileProgressRow> result =
                readingProgressService.fetchUserFileProgress(userId, bookIds);

        assertEquals(1, result.size());
        assertEquals(progress2, result.get(1L));
    }

    @Test
    void enrichBookWithProgress_shouldSetProgressFields() {
        Book book = Book.builder().id(1L).build();
        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setReadStatus(ReadStatus.READING);
        progress.setEpubProgress("cfi");
        progress.setEpubProgressPercent(50.0f);
        progress.setLastReadTime(Instant.now());

        readingProgressService.enrichBookWithProgress(book, progress);

        assertEquals("READING", book.getReadStatus());
        assertNotNull(book.getEpubProgress());
        assertEquals("cfi", book.getEpubProgress().getCfi());
        assertEquals(50.0f, book.getEpubProgress().getPercentage());
    }

    @Test
    void enrichBookWithProgress_withFileProgress_shouldOverlayFileProgress() {
        Book book = Book.builder().id(1L).build();

        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setReadStatus(ReadStatus.READING);
        progress.setEpubProgress("old-cfi");
        progress.setEpubProgressPercent(30.0f);
        progress.setLastReadTime(Instant.now().minusSeconds(100));

        UserBookFileProgressRow fileProgress = new UserBookFileProgressRow();
        fileProgress.setBookType(BookFileType.EPUB);
        fileProgress.setPositionData("new-cfi");
        fileProgress.setProgressPercent(50.0f);
        fileProgress.setLastReadTime(Instant.now());

        readingProgressService.enrichBookWithProgress(book, progress, fileProgress);

        assertEquals("new-cfi", book.getEpubProgress().getCfi());
        assertEquals(50.0f, book.getEpubProgress().getPercentage());
    }

    @Test
    void updateReadProgress_epub_shouldSaveProgress() {
        long bookId = 1L;
        BookEntity book = new BookEntity();
        book.setId(bookId);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setId(1L);
        primaryFile.setBook(book);
        primaryFile.setBookType(BookFileType.EPUB);
        book.setBookFiles(List.of(primaryFile));

        BookLoreUser user = mock(BookLoreUser.class);
        when(user.getId()).thenReturn(2L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findByIdWithBookFiles(bookId)).thenReturn(Optional.of(book));

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(userEntity));

        UserBookProgressRow progress = new UserBookProgressRow();
        when(jooqUserBookProgressRepository.findByUserIdAndBookId(2L, bookId)).thenReturn(Optional.of(progress));

        when(userBookFileProgressRepository.findByUserIdAndBookFileId(2L, 1L)).thenReturn(Optional.empty());

        ReadProgressRequest req = new ReadProgressRequest();
        req.setBookId(bookId);
        EpubProgress epubProgress = EpubProgress.builder().cfi("cfi").percentage(100f).build();
        req.setEpubProgress(epubProgress);

        readingProgressService.updateReadProgress(req);

        verify(jooqUserBookProgressRepository).save(progress);
        assertEquals("cfi", progress.getEpubProgress());
        assertEquals(ReadStatus.READ, progress.getReadStatus());
        assertEquals(100f, progress.getEpubProgressPercent());
    }

    @Test
    void updateReadProgress_pdf_shouldSaveProgress() {
        long bookId = 1L;
        BookEntity book = new BookEntity();
        book.setId(bookId);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setId(1L);
        primaryFile.setBook(book);
        primaryFile.setBookType(BookFileType.PDF);
        book.setBookFiles(List.of(primaryFile));

        BookLoreUser user = mock(BookLoreUser.class);
        when(user.getId()).thenReturn(2L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findByIdWithBookFiles(bookId)).thenReturn(Optional.of(book));

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(userEntity));

        UserBookProgressRow progress = new UserBookProgressRow();
        when(jooqUserBookProgressRepository.findByUserIdAndBookId(2L, bookId)).thenReturn(Optional.of(progress));

        when(userBookFileProgressRepository.findByUserIdAndBookFileId(2L, 1L)).thenReturn(Optional.empty());

        ReadProgressRequest req = new ReadProgressRequest();
        req.setBookId(bookId);
        PdfProgress pdfProgress = PdfProgress.builder().page(5).percentage(50f).build();
        req.setPdfProgress(pdfProgress);

        readingProgressService.updateReadProgress(req);

        verify(jooqUserBookProgressRepository).save(progress);
        assertEquals(5, progress.getPdfProgress());
        assertEquals(ReadStatus.READING, progress.getReadStatus());
        assertEquals(50f, progress.getPdfProgressPercent());
    }

    @Test
    void resetProgress_booklore_shouldCallBulkReset() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        BookLoreUser.UserPermissions permissions = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(permissions);
        when(permissions.isCanBulkResetBookloreReadProgress()).thenReturn(true);

        List<Long> bookIds = Arrays.asList(1L, 2L);
        when(jooqBookRepository.countByIds(bookIds)).thenReturn(2L);
        Set<Long> existing = new HashSet<>(bookIds);
        when(jooqUserBookProgressRepository.findExistingProgressBookIds(1L, bookIds)).thenReturn(existing);

        List<BookStatusUpdateResponse> result = readingProgressService.resetProgress(bookIds, ResetProgressType.BOOKLORE);

        verify(jooqUserBookProgressRepository).bulkResetBookloreProgress(eq(1L), eq(new ArrayList<>(bookIds)), any());
        assertEquals(2, result.size());
    }

    @Test
    void resetProgress_shouldAllowSingleBookWithoutPermission() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        BookLoreUser.UserPermissions permissions = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(permissions);
        when(permissions.isCanBulkResetBookloreReadProgress()).thenReturn(false);

        List<Long> bookIds = Collections.singletonList(1L);
        when(jooqBookRepository.countByIds(bookIds)).thenReturn(1L);
        Set<Long> existing = new HashSet<>(bookIds);
        when(jooqUserBookProgressRepository.findExistingProgressBookIds(1L, bookIds)).thenReturn(existing);

        assertDoesNotThrow(() -> readingProgressService.resetProgress(bookIds, ResetProgressType.BOOKLORE));
    }

    @Test
    void resetProgress_shouldThrowIfNoBulkPermission() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        BookLoreUser.UserPermissions permissions = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(permissions);
        when(permissions.isCanBulkResetBookloreReadProgress()).thenReturn(false);

        List<Long> bookIds = Arrays.asList(1L, 2L);
        when(jooqBookRepository.countByIds(bookIds)).thenReturn(2L);

        assertThrows(APIException.class, () -> readingProgressService.resetProgress(bookIds, ResetProgressType.BOOKLORE));
    }

    @Test
    void resetProgress_kobo_shouldCallKoboService() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        List<Long> bookIds = Collections.singletonList(1L);
        when(jooqBookRepository.countByIds(bookIds)).thenReturn(1L);
        Set<Long> existing = new HashSet<>(bookIds);
        when(jooqUserBookProgressRepository.findExistingProgressBookIds(1L, bookIds)).thenReturn(existing);

        readingProgressService.resetProgress(bookIds, ResetProgressType.KOBO);

        verify(jooqUserBookProgressRepository).bulkResetKoboProgress(eq(1L), anyList());
        verify(koboReadingStateService).deleteReadingState(1L);
    }
}
