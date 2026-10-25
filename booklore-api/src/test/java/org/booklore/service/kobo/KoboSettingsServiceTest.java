package org.booklore.service.kobo;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.request.ShelfCreateRequest;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.jooq.JooqKoboUserSettingsRepository;
import org.booklore.repository.jooq.dto.KoboUserSettings;
import org.booklore.service.ShelfService;
import org.booklore.service.hardcover.HardcoverSyncSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KoboSettingsServiceTest {

    @Mock
    private JooqKoboUserSettingsRepository repository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private ShelfService shelfService;
    @Mock
    private HardcoverSyncSettingsService hardcoverSyncSettingsService;

    @InjectMocks
    private KoboSettingsService service;

    private BookLoreUser user;
    private KoboUserSettings settings;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder().id(1L).isDefaultPassword(false).build();
        settings = rec("token", true, true, 0.5f, 0.9f);
    }

    private KoboUserSettings rec(String token, boolean syncEnabled, boolean autoAdd, Float reading, Float finished) {
        return new KoboUserSettings(10L, 1L, token, syncEnabled, reading, finished, autoAdd, null, false, false);
    }

    /** Echoes the token passed to insert(userId, token, syncEnabled) back into a record. */
    private void stubInsertEchoingToken() {
        when(repository.insert(anyLong(), anyString(), anyBoolean())).thenAnswer(inv ->
                new KoboUserSettings(10L, inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        1f, 99f, false, null, false, false));
    }

    /** Echoes the token passed to updateTokenByUserId(userId, token) into a record based on `settings`. */
    private void stubUpdateTokenEchoingToken() {
        when(repository.updateTokenByUserId(anyLong(), anyString())).thenAnswer(inv ->
                new KoboUserSettings(10L, inv.getArgument(0), inv.getArgument(1), settings.getSyncEnabled(),
                        settings.getProgressMarkAsReadingThreshold(), settings.getProgressMarkAsFinishedThreshold(),
                        settings.getAutoAddToShelf(), null, false, false));
    }

    /** Echoes the args of updateSettingsByUserId back into a record. */
    private void stubUpdateSettingsEchoingArgs() {
        when(repository.updateSettingsByUserId(anyLong(), anyBoolean(), any(), any(), anyBoolean(), anyBoolean()))
                .thenAnswer(inv -> new KoboUserSettings(10L, inv.getArgument(0), "token", inv.getArgument(1),
                        inv.getArgument(2), inv.getArgument(3), inv.getArgument(4), null, false, inv.getArgument(5)));
    }

    @Test
    void getCurrentUserSettings_existingSettings() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(settings);

        KoboSyncSettings dto = service.getCurrentUserSettings();

        assertEquals(settings.getId(), dto.getId());
        assertEquals(String.valueOf(settings.getUserId()), dto.getUserId());
        assertEquals(settings.getToken(), dto.getToken());
        assertTrue(dto.isSyncEnabled());
        assertTrue(dto.isAutoAddToShelf());
        assertEquals(settings.getProgressMarkAsReadingThreshold(), dto.getProgressMarkAsReadingThreshold());
        assertEquals(settings.getProgressMarkAsFinishedThreshold(), dto.getProgressMarkAsFinishedThreshold());
    }

    @Test
    void getCurrentUserSettings_noSettings_createsDefault() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(null);
        stubInsertEchoingToken();
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.empty());
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        KoboSyncSettings dto = service.getCurrentUserSettings();

        assertEquals(user.getId().toString(), dto.getUserId());
        assertNotNull(dto.getToken());
        assertFalse(dto.isSyncEnabled());
    }

    @Test
    void createOrUpdateToken_existingSettings() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(settings);
        stubUpdateTokenEchoingToken();
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.of(ShelfEntity.builder().id(100L).build()));

        KoboSyncSettings dto = service.createOrUpdateToken();

        assertEquals(String.valueOf(settings.getUserId()), dto.getUserId());
        assertNotNull(dto.getToken());
        assertNotEquals("token", dto.getToken());
    }

    @Test
    void createOrUpdateToken_noSettings_createsNew() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(null);
        stubInsertEchoingToken();
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.empty());
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        KoboSyncSettings dto = service.createOrUpdateToken();

        assertEquals(user.getId().toString(), dto.getUserId());
        assertNotNull(dto.getToken());
        assertFalse(dto.isSyncEnabled());
    }

    @Test
    void updateSettings_disableSync_deletesShelf() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(settings);
        Shelf shelf = Shelf.builder().id(100L).build();
        when(shelfService.getUserKoboShelf()).thenReturn(shelf);
        stubUpdateSettingsEchoingArgs();

        KoboSyncSettings update = new KoboSyncSettings();
        update.setSyncEnabled(false);
        update.setAutoAddToShelf(false);

        KoboSyncSettings dto = service.updateSettings(update);

        verify(shelfService).deleteShelf(100L);
        assertFalse(dto.isSyncEnabled());
        assertFalse(dto.isAutoAddToShelf());
    }

    @Test
    void updateSettings_enableSync_createsShelf() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        settings = rec("token", false, true, 0.5f, 0.9f);
        when(repository.findByUserId(1L)).thenReturn(settings);
        when(shelfService.getUserKoboShelf()).thenReturn(null);
        stubUpdateSettingsEchoingArgs();
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.empty());
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        KoboSyncSettings update = new KoboSyncSettings();
        update.setSyncEnabled(true);
        update.setAutoAddToShelf(true);

        KoboSyncSettings dto = service.updateSettings(update);

        verify(shelfService).createShelf(any(ShelfCreateRequest.class));
        assertTrue(dto.isSyncEnabled());
        assertTrue(dto.isAutoAddToShelf());
    }

    @Test
    void updateSettings_updatesThresholds() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(settings);
        stubUpdateSettingsEchoingArgs();

        KoboSyncSettings update = new KoboSyncSettings();
        update.setSyncEnabled(true);
        update.setAutoAddToShelf(true);
        update.setProgressMarkAsReadingThreshold(0.7f);
        update.setProgressMarkAsFinishedThreshold(0.95f);

        KoboSyncSettings dto = service.updateSettings(update);

        assertEquals((Float) 0.7f, dto.getProgressMarkAsReadingThreshold());
        assertEquals((Float) 0.95f, dto.getProgressMarkAsFinishedThreshold());
    }

    @Test
    void updateSettings_nullThresholds_shouldNotChangeExisting() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(settings);
        stubUpdateSettingsEchoingArgs();

        Float originalReading = settings.getProgressMarkAsReadingThreshold();
        Float originalFinished = settings.getProgressMarkAsFinishedThreshold();

        KoboSyncSettings update = new KoboSyncSettings();
        update.setSyncEnabled(true);
        update.setAutoAddToShelf(true);
        update.setProgressMarkAsReadingThreshold(null);
        update.setProgressMarkAsFinishedThreshold(null);

        KoboSyncSettings dto = service.updateSettings(update);

        assertEquals(originalReading, dto.getProgressMarkAsReadingThreshold());
        assertEquals(originalFinished, dto.getProgressMarkAsFinishedThreshold());
    }

    @Test
    void getCurrentUserSettings_settingsWithNullToken_shouldReturnDtoWithNullToken() {
        settings = rec(null, true, true, 0.5f, 0.9f);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(settings);

        KoboSyncSettings dto = service.getCurrentUserSettings();

        assertNull(dto.getToken());
    }

    @Test
    void getCurrentUserSettings_noAuthenticatedUser_shouldThrowException() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(null);
        assertThrows(NullPointerException.class, () -> service.getCurrentUserSettings());
    }

    @Test
    void updateSettings_getUserKoboShelfReturnsNull_shouldNotThrow() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(settings);
        when(shelfService.getUserKoboShelf()).thenReturn(null);
        stubUpdateSettingsEchoingArgs();

        KoboSyncSettings update = new KoboSyncSettings();
        update.setSyncEnabled(false);
        update.setAutoAddToShelf(false);

        assertDoesNotThrow(() -> service.updateSettings(update));
    }

    @Test
    void ensureKoboShelfExists_doesNotCreateIfExists() throws Exception {
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.of(ShelfEntity.builder().id(100L).build()));

        var method = service.getClass().getDeclaredMethod("ensureKoboShelfExists", Long.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(service, 1L));
        verify(shelfService, never()).createShelf(any());
    }

    @Test
    void ensureKoboShelfExists_createsIfMissing() throws Exception {
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.empty());
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        var method = service.getClass().getDeclaredMethod("ensureKoboShelfExists", Long.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(service, 1L));
        verify(shelfService).createShelf(any(ShelfCreateRequest.class));
    }

    @Test
    void ensureKoboShelfExists_idempotentIfCalledTwice() throws Exception {
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName())))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ShelfEntity.builder().id(100L).build()));
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        var method = service.getClass().getDeclaredMethod("ensureKoboShelfExists", Long.class);
        method.setAccessible(true);

        method.invoke(service, 1L);
        method.invoke(service, 1L);

        verify(shelfService, times(1)).createShelf(any(ShelfCreateRequest.class));
    }

    @Test
    void createOrUpdateToken_multipleCalls_generateDifferentTokens() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L))
                .thenReturn(null)
                .thenReturn(settings);
        stubInsertEchoingToken();
        stubUpdateTokenEchoingToken();
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.empty());
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        KoboSyncSettings dto1 = service.createOrUpdateToken();
        // Simulate a new call with an existing entity
        KoboSyncSettings dto2 = service.createOrUpdateToken();

        assertNotEquals(dto1.getToken(), dto2.getToken());
    }
}
