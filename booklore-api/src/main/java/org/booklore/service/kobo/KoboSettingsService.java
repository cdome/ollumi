package org.booklore.service.kobo;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.request.ShelfCreateRequest;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.IconType;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.jooq.JooqKoboUserSettingsRepository;
import org.booklore.repository.jooq.dto.KoboUserSettings;
import org.booklore.service.ShelfService;
import org.booklore.service.hardcover.HardcoverSyncSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KoboSettingsService {

    private final JooqKoboUserSettingsRepository repository;
    private final AuthenticationService authenticationService;
    private final ShelfService shelfService;
    private final HardcoverSyncSettingsService hardcoverSyncSettingsService;

    @Transactional(readOnly = true)
    public KoboSyncSettings getCurrentUserSettings() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        KoboUserSettings entity = repository.findByUserId(user.getId());
        if (entity == null) {
            entity = initDefaultSettings(user.getId());
        }
        return mapToDto(entity);
    }

    @Transactional
    public KoboSyncSettings createOrUpdateToken() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        String newToken = generateToken();

        KoboUserSettings existing = repository.findByUserId(user.getId());

        ensureKoboShelfExists(user.getId());

        KoboUserSettings entity = (existing != null)
                ? repository.updateTokenByUserId(user.getId(), newToken)
                : repository.insert(user.getId(), newToken, false);

        return mapToDto(entity);
    }

    @Transactional
    public KoboSyncSettings updateSettings(KoboSyncSettings settings) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        KoboUserSettings existing = repository.findByUserId(user.getId());
        if (existing == null) {
            existing = initDefaultSettings(user.getId());
        }

        boolean syncEnabled = existing.getSyncEnabled();
        if (settings.isSyncEnabled() != existing.getSyncEnabled()) {
            Shelf userKoboShelf = shelfService.getUserKoboShelf();
            if (!settings.isSyncEnabled()) {
                if (userKoboShelf != null) {
                    shelfService.deleteShelf(userKoboShelf.getId());
                }
            } else {
                ensureKoboShelfExists(user.getId());
            }
            syncEnabled = settings.isSyncEnabled();
        }

        Float readingThreshold = settings.getProgressMarkAsReadingThreshold() != null
                ? settings.getProgressMarkAsReadingThreshold()
                : existing.getProgressMarkAsReadingThreshold();
        Float finishedThreshold = settings.getProgressMarkAsFinishedThreshold() != null
                ? settings.getProgressMarkAsFinishedThreshold()
                : existing.getProgressMarkAsFinishedThreshold();

        KoboUserSettings entity = repository.updateSettingsByUserId(
                user.getId(),
                syncEnabled,
                readingThreshold,
                finishedThreshold,
                settings.isAutoAddToShelf(),
                settings.isTwoWayProgressSync());

        return mapToDto(entity, hardcoverSyncSettingsService.getSettingsForUserId(user.getId()));
    }

    private KoboUserSettings initDefaultSettings(Long userId) {
        ensureKoboShelfExists(userId);
        return repository.insert(userId, generateToken(), false);
    }

    private void ensureKoboShelfExists(Long userId) {
        Optional<ShelfEntity> shelf = shelfService.getShelf(userId, ShelfType.KOBO.getName());
        if (shelf.isEmpty()) {
            shelfService.createShelf(
                    ShelfCreateRequest.builder()
                            .name(ShelfType.KOBO.getName())
                            .icon(ShelfType.KOBO.getIcon())
                            .iconType(IconType.PRIME_NG)
                            .build()
            );
        }
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    private KoboSyncSettings mapToDto(KoboUserSettings entity) {
        HardcoverSyncSettings hardcoverSettings = hardcoverSyncSettingsService.getSettingsForUserId(entity.getUserId());
        return mapToDto(entity, hardcoverSettings);
    }

    private KoboSyncSettings mapToDto(KoboUserSettings entity, HardcoverSyncSettings hardcoverSettings) {
        KoboSyncSettings dto = new KoboSyncSettings();
        dto.setId(entity.getId());
        dto.setUserId(String.valueOf(entity.getUserId()));
        dto.setToken(entity.getToken());
        dto.setSyncEnabled(entity.getSyncEnabled());
        dto.setProgressMarkAsReadingThreshold(entity.getProgressMarkAsReadingThreshold());
        dto.setProgressMarkAsFinishedThreshold(entity.getProgressMarkAsFinishedThreshold());
        dto.setAutoAddToShelf(entity.getAutoAddToShelf());
        dto.setTwoWayProgressSync(entity.getTwoWayProgressSync());
        if (hardcoverSettings != null) {
            dto.setHardcoverApiKey(hardcoverSettings.getHardcoverApiKey());
            dto.setHardcoverSyncEnabled(hardcoverSettings.isHardcoverSyncEnabled());
        } else {
            dto.setHardcoverSyncEnabled(false);
        }
        return dto;
    }

    /**
     * Get Kobo settings for a specific user by ID.
     */
    @Transactional(readOnly = true)
    public KoboSyncSettings getSettingsByUserId(Long userId) {
        KoboUserSettings entity = repository.findByUserId(userId);
        return entity != null ? mapToDto(entity) : null;
    }

}
