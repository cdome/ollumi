package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.model.dto.kobo.KoboReadingState;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.KoboReadStatus;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.jooq.JooqKoboReadingStateRepository;
import org.booklore.repository.jooq.JooqUserBookProgressRepository;
import org.booklore.repository.jooq.dto.UserBookProgressRow;
import org.booklore.repository.UserRepository;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.booklore.service.kobo.KoboReadingStateBuilder;
import org.booklore.service.kobo.KoboReadingStateService;
import org.booklore.service.kobo.KoboSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.booklore.model.entity.BookFileEntity;
import org.booklore.repository.jooq.JooqUserBookFileProgressRepository;
import org.booklore.repository.jooq.dto.UserBookFileProgressRow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoboReadingStateServiceTest {

    @Mock
    private JooqKoboReadingStateRepository repository;

    @Mock
    private JooqUserBookProgressRepository progressRepository;
    
    @Mock
    private BookRepository bookRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private AuthenticationService authenticationService;
    
    @Mock
    private KoboSettingsService koboSettingsService;

    @Mock
    private KoboReadingStateBuilder readingStateBuilder;

    @Mock
    private HardcoverSyncService hardcoverSyncService;

    @Mock
    private JooqUserBookFileProgressRepository fileProgressRepository;

    @InjectMocks
    private KoboReadingStateService service;

    private BookLoreUser testUser;
    private BookEntity testBook;
    private BookLoreUserEntity testUserEntity;
    private KoboSyncSettings testSettings;

    @BeforeEach
    void setUp() {
        testUser = BookLoreUser.builder()
                .id(1L)
                .username("testuser")
                .isDefaultPassword(true).build();

        testUserEntity = new BookLoreUserEntity();
        testUserEntity.setId(1L);
        testUserEntity.setUsername("testuser");

        testBook = new BookEntity();
        testBook.setId(100L);

        testSettings = new KoboSyncSettings();
        testSettings.setProgressMarkAsReadingThreshold(1f);
        testSettings.setProgressMarkAsFinishedThreshold(99f);

        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(koboSettingsService.getCurrentUserSettings()).thenReturn(testSettings);
    }

    @Test
    @DisplayName("Should not overwrite existing finished date when syncing completed book")
    void testSyncKoboProgressToUserBookProgress_PreserveExistingFinishedDate() {
        String entitlementId = "100";
        testSettings.setProgressMarkAsFinishedThreshold(99f);

        Instant originalFinishedDate = Instant.parse("2025-01-15T10:30:00Z");
        UserBookProgressRow existingProgress = new UserBookProgressRow();
        existingProgress.setUserId(testUserEntity.getId());
        existingProgress.setBookId(testBook.getId());
        existingProgress.setKoboProgressPercent(99.5f);
        existingProgress.setReadStatus(ReadStatus.READ);
        existingProgress.setDateFinished(originalFinishedDate);

        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(100)
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));

        ArgumentCaptor<UserBookProgressRow> progressCaptor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(existingProgress);

        service.saveReadingState(List.of(readingState));

        UserBookProgressRow savedProgress = progressCaptor.getValue();
        assertEquals(100.0f, savedProgress.getKoboProgressPercent());
        assertEquals(ReadStatus.READ, savedProgress.getReadStatus());
        assertEquals(originalFinishedDate, savedProgress.getDateFinished(), 
            "Existing finished date should not be overwritten during sync");
    }

    @Test
    @DisplayName("Should not update Hardcover.app when progress hasn't changed")
    void testSyncKoboProgressToUserBookProgress_IgnoreHardcoverUpdateWhenNoChange() {
        String entitlementId = "100";
        testSettings.setProgressMarkAsFinishedThreshold(99f);

        Instant originalFinishedDate = Instant.parse("2025-01-15T10:30:00Z");
        UserBookProgressRow existingProgress = new UserBookProgressRow();
        existingProgress.setUserId(testUserEntity.getId());
        existingProgress.setBookId(testBook.getId());
        existingProgress.setKoboProgressPercent(12.0f);
        existingProgress.setReadStatus(ReadStatus.READING);
        existingProgress.setDateFinished(originalFinishedDate);

        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(12)
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));

        ArgumentCaptor<UserBookProgressRow> progressCaptor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(existingProgress);

        service.saveReadingState(List.of(readingState));

        UserBookProgressRow savedProgress = progressCaptor.getValue();
        assertEquals(12.0f, savedProgress.getKoboProgressPercent());
        assertEquals(ReadStatus.READING, savedProgress.getReadStatus());
        assertEquals(originalFinishedDate, savedProgress.getDateFinished(), 
            "Existing finished date should not be overwritten during sync");
        verify(hardcoverSyncService, never()).syncProgressToHardcover(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle invalid entitlement ID gracefully")
    void testSyncKoboProgressToUserBookProgress_InvalidEntitlementId() {
        String entitlementId = "not-a-number";
        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder().build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);

        assertDoesNotThrow(() -> service.saveReadingState(List.of(readingState)));
        verify(progressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle missing book gracefully")
    void testSyncKoboProgressToUserBookProgress_BookNotFound() {
        String entitlementId = "999";
        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(50)
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.saveReadingState(List.of(readingState)));
        verify(progressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should construct reading state from UserBookProgress when no Kobo state exists")
    void testGetReadingState_ConstructFromProgress() {
        String entitlementId = "100";
        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setKoboProgressPercent(75.5f);
        progress.setKoboLocation("epubcfi(/6/4[chap01ref]!/4/2/1:3)");
        progress.setKoboLocationType("EpubCfi");
        progress.setKoboLocationSource("Kobo");
        progress.setKoboProgressReceivedTime(Instant.now());

        KoboReadingState expectedState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(75)
                        .location(KoboReadingState.CurrentBookmark.Location.builder()
                                .value("epubcfi(/6/4[chap01ref]!/4/2/1:3)")
                                .type("EpubCfi")
                                .source("Kobo")
                                .build())
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(progress));
        when(readingStateBuilder.buildReadingStateFromProgress(entitlementId, progress)).thenReturn(expectedState);

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertNotNull(result);
        assertEquals(1, result.size());
        
        KoboReadingState state = result.getFirst();
        assertEquals(entitlementId, state.getEntitlementId());
        assertNotNull(state.getCurrentBookmark());
        assertEquals(75, state.getCurrentBookmark().getProgressPercent());
        assertNotNull(state.getCurrentBookmark().getLocation());
        assertEquals("epubcfi(/6/4[chap01ref]!/4/2/1:3)", state.getCurrentBookmark().getLocation().getValue());
        assertEquals("EpubCfi", state.getCurrentBookmark().getLocation().getType());
        assertEquals("Kobo", state.getCurrentBookmark().getLocation().getSource());
        
        verify(repository).findByEntitlementIdAndUserId(entitlementId, 1L);
        verify(progressRepository, atLeastOnce()).findByUserIdAndBookId(1L, 100L);
        verify(readingStateBuilder).buildReadingStateFromProgress(entitlementId, progress);
    }

    @Test
    @DisplayName("Should return null when no Kobo reading state exists and UserBookProgress has no Kobo data")
    void testGetReadingState_NoKoboDataInProgress() {
        String entitlementId = "100";
        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setKoboProgressPercent(null);
        progress.setKoboLocation(null);

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(progress));

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findByEntitlementIdAndUserId(entitlementId, 1L);
        verify(progressRepository).findByUserIdAndBookId(1L, 100L);
    }

    @Test
    @DisplayName("Should return null when no Kobo state and no UserBookProgress exists")
    void testGetReadingState_NoDataExists() {
        String entitlementId = "100";
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(progressRepository).findByUserIdAndBookId(1L, 100L);
    }

    @Test
    @DisplayName("Should return existing Kobo reading state when it exists")
    void testGetReadingState_ExistingState() {
        String entitlementId = "100";
        KoboReadingState existingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(existingState);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(entitlementId, result.getFirst().getEntitlementId());
    }

    @Test
    @DisplayName("Should handle null bookmark gracefully")
    void testSyncKoboProgressToUserBookProgress_NullBookmark() {
        String entitlementId = "100";
        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(null)
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressRow> progressCaptor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(new UserBookProgressRow());

        assertDoesNotThrow(() -> service.saveReadingState(List.of(readingState)));

        UserBookProgressRow savedProgress = progressCaptor.getValue();
        assertNull(savedProgress.getKoboProgressPercent());
        assertNotNull(savedProgress.getKoboProgressReceivedTime());
    }

    @Test
    @DisplayName("Should handle null progress percent in bookmark")
    void testSyncKoboProgressToUserBookProgress_NullProgressPercent() {
        String entitlementId = "100";
        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(null)
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressRow> progressCaptor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(new UserBookProgressRow());

        assertDoesNotThrow(() -> service.saveReadingState(List.of(readingState)));

        UserBookProgressRow savedProgress = progressCaptor.getValue();
        assertNull(savedProgress.getKoboProgressPercent());
    }

    @Test
    @DisplayName("Should merge per-field updates based on lastModified")
    void testSaveReadingState_PerFieldMerge() throws Exception {
        String entitlementId = "100";
        String existingTimestamp = "2025-01-01T00:00:00.0000000Z";
        String newerTimestamp = "2025-01-04T00:00:00.0000000Z";
        String midTimestamp = "2025-01-03T00:00:00.0000000Z";
        String olderTimestamp = "2025-01-02T00:00:00.0000000Z";

        KoboReadingState.StatusInfo existingStatus = KoboReadingState.StatusInfo.builder()
                .lastModified(existingTimestamp)
                .status(KoboReadStatus.READING)
                .timesStartedReading(1)
                .build();
        KoboReadingState.Statistics existingStats = KoboReadingState.Statistics.builder()
                .lastModified(existingTimestamp)
                .spentReadingMinutes(5)
                .remainingTimeMinutes(20)
                .build();
        KoboReadingState.CurrentBookmark existingBookmark = KoboReadingState.CurrentBookmark.builder()
                .lastModified(midTimestamp)
                .progressPercent(25)
                .build();

        KoboReadingState existingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .created(existingTimestamp)
                .lastModified(midTimestamp)
                .statusInfo(existingStatus)
                .statistics(existingStats)
                .currentBookmark(existingBookmark)
                .priorityTimestamp(midTimestamp)
                .build();

        KoboReadingState.StatusInfo incomingStatus = KoboReadingState.StatusInfo.builder()
                .lastModified(newerTimestamp)
                .status(KoboReadStatus.FINISHED)
                .timesStartedReading(2)
                .build();
        KoboReadingState.CurrentBookmark incomingBookmark = KoboReadingState.CurrentBookmark.builder()
                .lastModified(olderTimestamp)
                .progressPercent(10)
                .build();

        KoboReadingState incomingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .lastModified(newerTimestamp)
                .statusInfo(incomingStatus)
                .currentBookmark(incomingBookmark)
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(existingState);

        ArgumentCaptor<KoboReadingState> stateCaptor = ArgumentCaptor.forClass(KoboReadingState.class);
        service.saveReadingState(List.of(incomingState));
        verify(repository).updateByEntitlementIdAndUserId(eq(entitlementId), eq(1L), stateCaptor.capture());

        KoboReadingState saved = stateCaptor.getValue();
        KoboReadingState.StatusInfo savedStatus = saved.getStatusInfo();
        KoboReadingState.CurrentBookmark savedBookmark = saved.getCurrentBookmark();
        KoboReadingState.Statistics savedStatistics = saved.getStatistics();

        assertEquals(incomingStatus.getStatus(), savedStatus.getStatus());
        assertEquals(existingBookmark.getProgressPercent(), savedBookmark.getProgressPercent());
        assertEquals(existingStats.getSpentReadingMinutes(), savedStatistics.getSpentReadingMinutes());
        assertEquals(newerTimestamp, saved.getLastModified());
        assertEquals(newerTimestamp, saved.getPriorityTimestamp());
    }

    @Test
    @DisplayName("Should not update fields when timestamps are equal")
    void testSaveReadingState_EqualTimestampNoUpdate() throws Exception {
        String entitlementId = "100";
        String timestamp = "2025-01-01T00:00:00.0000000Z";

        KoboReadingState.StatusInfo existingStatus = KoboReadingState.StatusInfo.builder()
                .lastModified(timestamp)
                .status(KoboReadStatus.READING)
                .timesStartedReading(1)
                .build();

        KoboReadingState existingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .created(timestamp)
                .lastModified(timestamp)
                .statusInfo(existingStatus)
                .priorityTimestamp(timestamp)
                .build();

        KoboReadingState.StatusInfo incomingStatus = KoboReadingState.StatusInfo.builder()
                .lastModified(timestamp)
                .status(KoboReadStatus.FINISHED)
                .timesStartedReading(2)
                .build();

        KoboReadingState incomingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .lastModified(timestamp)
                .statusInfo(incomingStatus)
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(existingState);

        ArgumentCaptor<KoboReadingState> stateCaptor = ArgumentCaptor.forClass(KoboReadingState.class);
        service.saveReadingState(List.of(incomingState));
        verify(repository).updateByEntitlementIdAndUserId(eq(entitlementId), eq(1L), stateCaptor.capture());

        KoboReadingState saved = stateCaptor.getValue();
        KoboReadingState.StatusInfo savedStatus = saved.getStatusInfo();

        assertEquals(existingStatus.getStatus(), savedStatus.getStatus());
        assertEquals(timestamp, saved.getLastModified());
    }

    @Test
    @DisplayName("Should delete reading state for authenticated user")
    void testDeleteReadingState() {
        service.deleteReadingState(100L);

        verify(repository).deleteByEntitlementIdAndUserId("100", 1L);
    }

    @Test
    @DisplayName("Should not throw when deleting non-existent reading state")
    void testDeleteReadingState_notFound() {
        assertDoesNotThrow(() -> service.deleteReadingState(100L));
        verify(repository).deleteByEntitlementIdAndUserId("100", 1L);
    }

    @Test
    @DisplayName("Should overlay web reader bookmark when two-way sync is ON and web reader data exists")
    void testGetReadingState_overlayWebReaderBookmark() {
        testSettings.setTwoWayProgressSync(true);
        String entitlementId = "100";

        KoboReadingState existingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(30)
                        .contentSourceProgressPercent(45)
                        .location(KoboReadingState.CurrentBookmark.Location.builder()
                                .value("epubcfi(/6/10)")
                                .type("EpubCfi")
                                .source("Kobo")
                                .build())
                        .lastModified("2025-01-01T00:00:00Z")
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(existingState);

        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setEpubProgress("epubcfi(/6/20)");
        progress.setEpubProgressPercent(65f);
        progress.setLastReadTime(Instant.parse("2025-06-01T10:00:00Z"));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(progress));

        KoboReadingState.CurrentBookmark webBookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(65)
                .lastModified("2025-06-01T10:00:00Z")
                .build();
        when(readingStateBuilder.buildBookmarkFromProgress(progress)).thenReturn(webBookmark);

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertEquals(1, result.size());
        KoboReadingState.CurrentBookmark bookmark = result.getFirst().getCurrentBookmark();
        assertEquals(65, bookmark.getProgressPercent());
        assertEquals(45, bookmark.getContentSourceProgressPercent());
        assertNotNull(bookmark.getLocation());
        assertEquals("epubcfi(/6/10)", bookmark.getLocation().getValue());
    }

    @Test
    @DisplayName("Should NOT overlay web reader bookmark when two-way sync is OFF")
    void testGetReadingState_noOverlayWhenToggleOff() {
        testSettings.setTwoWayProgressSync(false);
        String entitlementId = "100";

        KoboReadingState existingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(30)
                        .lastModified("2025-01-01T00:00:00Z")
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(existingState);

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertEquals(1, result.size());
        assertEquals(30, result.getFirst().getCurrentBookmark().getProgressPercent());
        verify(readingStateBuilder, never()).buildBookmarkFromProgress(any());
    }

    @Test
    @DisplayName("Should cross-populate epub fields from Kobo when two-way sync is ON")
    void testSyncKoboProgress_crossPopulateEpubFields() {
        testSettings.setTwoWayProgressSync(true);
        String entitlementId = "100";

        BookFileEntity primaryFile = BookFileEntity.builder().id(10L).build();
        testBook.setBookFiles(List.of(primaryFile));

        UserBookProgressRow existingProgress = new UserBookProgressRow();
        existingProgress.setUserId(testUserEntity.getId());
        existingProgress.setBookId(testBook.getId());

        KoboReadingState.CurrentBookmark.Location location = KoboReadingState.CurrentBookmark.Location.builder()
                .value("epubcfi(/6/8)")
                .type("EpubCfi")
                .source("chapter3.xhtml")
                .build();

        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(55)
                .location(location)
                .lastModified("2025-06-15T12:00:00Z")
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
        when(fileProgressRepository.findByUserIdAndBookFileId(1L, 10L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressRow> captor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

        service.saveReadingState(List.of(readingState));

        UserBookProgressRow saved = captor.getValue();
        assertEquals(55f, saved.getEpubProgressPercent());
        assertEquals("chapter3.xhtml", saved.getEpubProgressHref());
        assertNull(saved.getEpubProgress());
    }

    @Test
    @DisplayName("Should NOT cross-populate epub fields when two-way sync is OFF")
    void testSyncKoboProgress_noCrossPopulateWhenToggleOff() {
        testSettings.setTwoWayProgressSync(false);
        String entitlementId = "100";

        UserBookProgressRow existingProgress = new UserBookProgressRow();
        existingProgress.setUserId(testUserEntity.getId());
        existingProgress.setBookId(testBook.getId());
        existingProgress.setEpubProgressPercent(40f);
        existingProgress.setEpubProgress("epubcfi(/6/4)");

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(70)
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));

        ArgumentCaptor<UserBookProgressRow> captor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

        service.saveReadingState(List.of(readingState));

        UserBookProgressRow saved = captor.getValue();
        assertEquals(40f, saved.getEpubProgressPercent());
        assertEquals("epubcfi(/6/4)", saved.getEpubProgress());
    }

    @Test
    @DisplayName("Should not cross-populate when web reader has newer progress")
    void testSyncKoboProgress_skipCrossPopulateWhenWebReaderNewer() {
        testSettings.setTwoWayProgressSync(true);
        String entitlementId = "100";

        BookFileEntity primaryFile = BookFileEntity.builder().id(10L).build();
        testBook.setBookFiles(List.of(primaryFile));

        UserBookFileProgressRow fileProgress = new UserBookFileProgressRow();
        fileProgress.setId(1L);
        fileProgress.setBookFileId(10L);
        fileProgress.setLastReadTime(Instant.now().plusSeconds(3600));

        UserBookProgressRow existingProgress = new UserBookProgressRow();
        existingProgress.setUserId(testUserEntity.getId());
        existingProgress.setBookId(testBook.getId());
        existingProgress.setEpubProgressPercent(80f);
        existingProgress.setEpubProgress("epubcfi(/6/20)");

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(50)
                        .lastModified("2025-01-01T00:00:00Z")
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
        when(fileProgressRepository.findByUserIdAndBookFileId(1L, 10L)).thenReturn(Optional.of(fileProgress));

        ArgumentCaptor<UserBookProgressRow> captor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

        service.saveReadingState(List.of(readingState));

        UserBookProgressRow saved = captor.getValue();
        assertEquals(80f, saved.getEpubProgressPercent());
        assertEquals("epubcfi(/6/20)", saved.getEpubProgress());
    }

    @Test
    @DisplayName("Should construct reading state from web reader progress when toggle ON and no Kobo data")
    void testGetReadingState_constructFromWebReaderProgress_whenToggleOn() {
        testSettings.setTwoWayProgressSync(true);
        String entitlementId = "100";

        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setKoboProgressPercent(null);
        progress.setKoboLocation(null);
        progress.setEpubProgressPercent(60f);
        progress.setEpubProgress("epubcfi(/6/12)");

        KoboReadingState expectedState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(60)
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(progress));
        when(readingStateBuilder.buildReadingStateFromProgress(entitlementId, progress)).thenReturn(expectedState);

        KoboReadingState.CurrentBookmark webBookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(60)
                .lastModified("2025-06-01T10:00:00Z")
                .build();
        when(readingStateBuilder.buildBookmarkFromProgress(progress)).thenReturn(webBookmark);

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertEquals(1, result.size());
        verify(readingStateBuilder).buildReadingStateFromProgress(entitlementId, progress);
    }

    @Test
    @DisplayName("Should NOT construct reading state from web reader progress when toggle OFF")
    void testGetReadingState_noConstructFromWebReaderProgress_whenToggleOff() {
        testSettings.setTwoWayProgressSync(false);
        String entitlementId = "100";

        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setKoboProgressPercent(null);
        progress.setKoboLocation(null);
        progress.setEpubProgressPercent(60f);

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(progress));

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertTrue(result.isEmpty());
        verify(readingStateBuilder, never()).buildReadingStateFromProgress(any(), any());
    }

    @Test
    @DisplayName("Should sync location data from Kobo bookmark")
    void testSyncKoboProgress_locationData() {
        String entitlementId = "100";

        KoboReadingState.CurrentBookmark.Location location = KoboReadingState.CurrentBookmark.Location.builder()
                .value("epubcfi(/6/4[chap01ref]!/4/2/1:3)")
                .type("EpubCfi")
                .source("Kobo")
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(42)
                        .location(location)
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressRow> captor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(captor.capture())).thenReturn(new UserBookProgressRow());

        service.saveReadingState(List.of(readingState));

        UserBookProgressRow saved = captor.getValue();
        assertEquals("epubcfi(/6/4[chap01ref]!/4/2/1:3)", saved.getKoboLocation());
        assertEquals("EpubCfi", saved.getKoboLocationType());
        assertEquals("Kobo", saved.getKoboLocationSource());
        assertEquals(42f, saved.getKoboProgressPercent());
    }

    @Test
    @DisplayName("Should set finished date when progress reaches threshold for new book")
    void testSyncKoboProgress_setsFinishedDate() {
        testSettings.setProgressMarkAsFinishedThreshold(99f);
        String entitlementId = "100";

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(100)
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressRow> captor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(captor.capture())).thenReturn(new UserBookProgressRow());

        service.saveReadingState(List.of(readingState));

        UserBookProgressRow saved = captor.getValue();
        assertEquals(ReadStatus.READ, saved.getReadStatus());
        assertNotNull(saved.getDateFinished());
    }

    @Test
    @DisplayName("Should use configurable thresholds for status derivation")
    void testSyncKoboProgress_configurableThresholds() {
        testSettings.setProgressMarkAsReadingThreshold(10f);
        testSettings.setProgressMarkAsFinishedThreshold(95f);
        String entitlementId = "100";

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(5)
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressRow> captor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(captor.capture())).thenReturn(new UserBookProgressRow());

        service.saveReadingState(List.of(readingState));

        assertEquals(ReadStatus.UNREAD, captor.getValue().getReadStatus());
    }

    @Test
    @DisplayName("Should trigger Hardcover sync when progress changes")
    void testSyncKoboProgress_triggersHardcover() {
        String entitlementId = "100";

        UserBookProgressRow existingProgress = new UserBookProgressRow();
        existingProgress.setUserId(testUserEntity.getId());
        existingProgress.setBookId(testBook.getId());
        existingProgress.setKoboProgressPercent(30f);

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(50)
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));

        ArgumentCaptor<UserBookProgressRow> captor = ArgumentCaptor.forClass(UserBookProgressRow.class);
        when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

        service.saveReadingState(List.of(readingState));

        verify(hardcoverSyncService).syncProgressToHardcover(eq(100L), eq(50f), eq(1L));
    }

    @Test
    @DisplayName("Should handle multiple reading states in a single save call")
    void testSaveReadingState_multipleStates() {
        KoboReadingState state1 = KoboReadingState.builder()
                .entitlementId("100")
                .currentBookmark(KoboReadingState.CurrentBookmark.builder().progressPercent(25).build())
                .build();
        KoboReadingState state2 = KoboReadingState.builder()
                .entitlementId("200")
                .currentBookmark(KoboReadingState.CurrentBookmark.builder().progressPercent(75).build())
                .build();

        BookEntity book2 = new BookEntity();
        book2.setId(200L);

        when(repository.findByEntitlementIdAndUserId(anyString(), eq(1L))).thenReturn(null);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(bookRepository.findById(200L)).thenReturn(Optional.of(book2));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(eq(1L), anyLong())).thenReturn(Optional.empty());
        when(progressRepository.save(any())).thenReturn(new UserBookProgressRow());

        assertDoesNotThrow(() -> service.saveReadingState(List.of(state1, state2)));

        verify(progressRepository, times(2)).save(any());
    }
}
