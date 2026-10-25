package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.jooq.dto.KoboUserSettings;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqKoboUserSettingsRepository;
import org.booklore.repository.jooq.JooqShelfRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboAutoShelfService {

    private final JooqKoboUserSettingsRepository koboUserSettingsRepository;
    private final JooqShelfRepository jooqShelfRepository;
    private final JooqBookEntityRepository jooqBookEntityRepository;
    private final KoboCompatibilityService koboCompatibilityService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoAddBookToKoboShelves(Long bookId) {
        if (bookId == null) {
            log.warn("Book ID is null for auto-add to Kobo shelf");
            return;
        }

        BookEntity book = jooqBookEntityRepository.findByIdFull(bookId);
        if (!isBookEligible(book)) {
            return;
        }

        List<KoboUserSettings> eligibleUsers = koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue();

        if (eligibleUsers.isEmpty()) {
            log.debug("No Kobo auto-add enabled users for book {}", book.getId());
            return;
        }

        List<Long> userIds = eligibleUsers.stream()
                .map(KoboUserSettings::getUserId)
                .toList();

        List<ShelfEntity> shelves = jooqShelfRepository.findByUserIdInAndName(userIds, ShelfType.KOBO.getName());

        Map<Long, Long> shelfIdByUser = shelves
                .stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), ShelfEntity::getId));

        Set<Long> existingShelfIds = jooqShelfRepository.findShelfIdsByBookId(book.getId());

        for (KoboUserSettings setting : eligibleUsers) {
            Long shelfId = shelfIdByUser.get(setting.getUserId());

            if (shelfId == null) {
                log.debug("User {} has auto-add enabled but no Kobo shelf exists", setting.getUserId());
                continue;
            }

            if (existingShelfIds.contains(shelfId)) {
                log.debug("Book {} already on Kobo shelf for user {}", book.getId(), setting.getUserId());
                continue;
            }

            jooqShelfRepository.assignShelfToBook(book.getId(), shelfId);
            log.info("Auto-added book {} to Kobo shelf for user {}", book.getId(), setting.getUserId());
        }
    }

    private boolean isBookEligible(BookEntity book) {
        if (book == null) {
            log.warn("Book not found for Kobo auto-add");
            return false;
        }

        if (!koboCompatibilityService.isBookSupportedForKobo(book)) {
            log.debug("Book {} is not Kobo-compatible, skipping", book.getId());
            return false;
        }

        return true;
    }
}
