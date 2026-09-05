package org.booklore.service.hardcover;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.dto.settings.UserSettingKey;
import org.booklore.repository.jooq.dto.KoboUserSettings;
import org.booklore.repository.jooq.dto.UserSettingRow;
import org.booklore.repository.jooq.JooqKoboUserSettingsRepository;
import org.booklore.repository.jooq.JooqUserSettingRepository;
import org.booklore.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HardcoverSyncSettingsService {

    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final JooqKoboUserSettingsRepository koboUserSettingsRepository;
    private final JooqUserSettingRepository userSettingRepository;

    @Transactional
    public HardcoverSyncSettings getCurrentUserSettings() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        return getSettingsForUserId(user.getId());
    }

    @Transactional
    public HardcoverSyncSettings getSettingsForUserId(Long userId) {
        userRepository.findByIdWithSettings(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));
        return readSettings(userId);
    }

    @Transactional
    public HardcoverSyncSettings updateCurrentUserSettings(HardcoverSyncSettings settings) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        return updateSettingsForUserId(user.getId(), settings);
    }

    @Transactional
    public HardcoverSyncSettings updateSettingsForUserId(Long userId, HardcoverSyncSettings settings) {
        if (settings == null) {
            throw ApiError.INVALID_INPUT.createException("Hardcover settings cannot be null.");
        }

        userRepository.findByIdWithSettings(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        String apiKey = settings.getHardcoverApiKey();
        if (apiKey == null) {
            apiKey = "";
        } else {
            apiKey = apiKey.trim();
        }
        upsertSetting(userId, UserSettingKey.HARDCOVER_API_KEY, apiKey);
        upsertSetting(userId, UserSettingKey.HARDCOVER_SYNC_ENABLED, Boolean.toString(settings.isHardcoverSyncEnabled()));

        HardcoverSyncSettings updated = new HardcoverSyncSettings();
        updated.setHardcoverApiKey(apiKey);
        updated.setHardcoverSyncEnabled(settings.isHardcoverSyncEnabled());
        return updated;
    }

    private HardcoverSyncSettings readSettings(Long userId) {
        UserSettingRow apiKeySetting = findSetting(userId, UserSettingKey.HARDCOVER_API_KEY).orElse(null);
        UserSettingRow syncEnabledSetting = findSetting(userId, UserSettingKey.HARDCOVER_SYNC_ENABLED).orElse(null);

        String apiKey = apiKeySetting != null ? apiKeySetting.getSettingValue() : null;
        boolean syncEnabled = syncEnabledSetting != null && Boolean.parseBoolean(syncEnabledSetting.getSettingValue());

        // One-time migration from the legacy kobo_user_settings columns to user_settings rows.
        if (apiKeySetting == null || syncEnabledSetting == null) {
            KoboUserSettings legacySettings = koboUserSettingsRepository.findByUserId(userId);
            if (legacySettings != null) {
                if (apiKeySetting == null && legacySettings.getHardcoverApiKey() != null && !legacySettings.getHardcoverApiKey().isBlank()) {
                    apiKey = legacySettings.getHardcoverApiKey();
                    upsertSetting(userId, UserSettingKey.HARDCOVER_API_KEY, apiKey);
                }
                if (syncEnabledSetting == null && legacySettings.getHardcoverSyncEnabled()) {
                    syncEnabled = legacySettings.getHardcoverSyncEnabled();
                    upsertSetting(userId, UserSettingKey.HARDCOVER_SYNC_ENABLED, Boolean.toString(syncEnabled));
                }
            }
        }

        HardcoverSyncSettings settings = new HardcoverSyncSettings();
        settings.setHardcoverApiKey(apiKey);
        settings.setHardcoverSyncEnabled(syncEnabled);
        return settings;
    }

    private Optional<UserSettingRow> findSetting(Long userId, UserSettingKey key) {
        return userSettingRepository.findByUserIdAndKey(userId, key.getDbKey());
    }

    private void upsertSetting(Long userId, UserSettingKey key, String value) {
        userSettingRepository.upsertSetting(userId, key.getDbKey(), value);
    }
}
