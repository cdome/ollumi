package org.booklore.service.hardcover;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.dto.settings.UserSettingKey;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.jooq.dto.KoboUserSettings;
import org.booklore.repository.jooq.JooqKoboUserSettingsRepository;
import org.booklore.repository.jooq.JooqUserSettingRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HardcoverSyncSettingsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private JooqKoboUserSettingsRepository koboUserSettingsRepository;

    @Mock
    private JooqUserSettingRepository userSettingRepository;

    private HardcoverSyncSettingsService service;

    @BeforeEach
    void setUp() {
        service = new HardcoverSyncSettingsService(userRepository, authenticationService, koboUserSettingsRepository, userSettingRepository);
    }

    @Test
    @DisplayName("Should fallback to legacy Kobo settings and persist user settings")
    void getSettingsForUserId_whenLegacyPresent_shouldPersistUserSettings() {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(1L);

        KoboUserSettings legacy = new KoboUserSettings(1L, 1L, "token", false, 1f, 99f, false, "legacy-key", true, false);

        when(userRepository.findByIdWithSettings(1L)).thenReturn(Optional.of(user));
        when(userSettingRepository.findByUserIdAndKey(1L, UserSettingKey.HARDCOVER_API_KEY.getDbKey())).thenReturn(Optional.empty());
        when(userSettingRepository.findByUserIdAndKey(1L, UserSettingKey.HARDCOVER_SYNC_ENABLED.getDbKey())).thenReturn(Optional.empty());
        when(koboUserSettingsRepository.findByUserId(1L)).thenReturn(legacy);

        HardcoverSyncSettings settings = service.getSettingsForUserId(1L);

        assertEquals("legacy-key", settings.getHardcoverApiKey());
        assertTrue(settings.isHardcoverSyncEnabled());
        verify(userSettingRepository).upsertSetting(1L, UserSettingKey.HARDCOVER_API_KEY.getDbKey(), "legacy-key");
        verify(userSettingRepository).upsertSetting(1L, UserSettingKey.HARDCOVER_SYNC_ENABLED.getDbKey(), "true");
    }

    @Test
    @DisplayName("Should trim API key when updating settings")
    void updateSettingsForUserId_shouldTrimApiKey() {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(1L);

        when(userRepository.findByIdWithSettings(1L)).thenReturn(Optional.of(user));

        HardcoverSyncSettings update = new HardcoverSyncSettings();
        update.setHardcoverApiKey("  new-key  ");
        update.setHardcoverSyncEnabled(false);

        HardcoverSyncSettings result = service.updateSettingsForUserId(1L, update);

        assertEquals("new-key", result.getHardcoverApiKey());
        verify(userSettingRepository).upsertSetting(1L, UserSettingKey.HARDCOVER_API_KEY.getDbKey(), "new-key");
        verify(userSettingRepository).upsertSetting(1L, UserSettingKey.HARDCOVER_SYNC_ENABLED.getDbKey(), "false");
    }
}
