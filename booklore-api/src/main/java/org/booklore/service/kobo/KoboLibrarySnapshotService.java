package org.booklore.service.kobo;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.*;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.jooq.JooqKoboDeletedBookProgressRepository;
import org.booklore.repository.jooq.JooqKoboLibrarySnapshotRepository;
import org.booklore.repository.jooq.JooqKoboSnapshotBookRepository;
import org.booklore.repository.jooq.dto.KoboLibrarySnapshot;
import org.booklore.repository.jooq.dto.KoboSnapshotBook;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class KoboLibrarySnapshotService {

    private final JooqKoboLibrarySnapshotRepository koboLibrarySnapshotRepository;
    private final JooqKoboSnapshotBookRepository koboSnapshotBookRepository;
    private final ShelfRepository shelfRepository;
    private final JooqKoboDeletedBookProgressRepository koboDeletedBookProgressRepository;
    private final KoboCompatibilityService koboCompatibilityService;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public Optional<KoboLibrarySnapshot> findByIdAndUserId(String id, Long userId) {
        return Optional.ofNullable(koboLibrarySnapshotRepository.findByIdAndUserId(id, userId));
    }

    @Transactional
    public KoboLibrarySnapshot create(Long userId) {
        String snapshotId = UUID.randomUUID().toString();
        List<KoboSnapshotBook> books = mapBooksToKoboSnapshotBook(getKoboShelf(userId), userId);
        return koboLibrarySnapshotRepository.insert(snapshotId, userId, LocalDateTime.now(), books);
    }

    @Transactional
    public Page<KoboSnapshotBook> getUnsyncedBooks(String snapshotId, Pageable pageable) {
        Page<KoboSnapshotBook> page = koboSnapshotBookRepository.findBySnapshotIdAndSyncedFalse(snapshotId, pageable);
        List<Long> bookIds = page.getContent().stream()
                .map(KoboSnapshotBook::getBookId)
                .toList();
        if (!bookIds.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(snapshotId, bookIds);
        }
        return page;
    }

    @Transactional
    public void updateSyncedStatusForExistingBooks(String previousSnapshotId, String currentSnapshotId) {
        List<KoboSnapshotBook> list = koboSnapshotBookRepository.findUnchangedBooksBetweenSnapshots(previousSnapshotId, currentSnapshotId);
        List<Long> unchangedBooks = list.stream()
                .map(KoboSnapshotBook::getBookId)
                .toList();

        if (!unchangedBooks.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(currentSnapshotId, unchangedBooks);
        }
    }

    @Transactional
    public Page<KoboSnapshotBook> getNewlyAddedBooks(String previousSnapshotId, String currentSnapshotId, Pageable pageable, Long userId) {
        Page<KoboSnapshotBook> page = koboSnapshotBookRepository.findNewlyAddedBooks(previousSnapshotId, currentSnapshotId, true, pageable);
        List<Long> newlyAddedBookIds = page.getContent().stream()
                .map(KoboSnapshotBook::getBookId)
                .toList();

        if (!newlyAddedBookIds.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(currentSnapshotId, newlyAddedBookIds);
        }

        return page;
    }

    @Transactional
    public Page<KoboSnapshotBook> getRemovedBooks(String previousSnapshotId, String currentSnapshotId, Long userId, Pageable pageable) {
        Page<KoboSnapshotBook> page = koboSnapshotBookRepository.findRemovedBooks(previousSnapshotId, currentSnapshotId, pageable);

        List<Long> bookIds = page.getContent().stream()
                .map(KoboSnapshotBook::getBookId)
                .toList();

        koboDeletedBookProgressRepository.insertAll(currentSnapshotId, userId, bookIds);
        return page;
    }

    @Transactional
    public Page<KoboSnapshotBook> getChangedBooks(String previousSnapshotId, String currentSnapshotId, Pageable pageable) {
        Page<KoboSnapshotBook> page = koboSnapshotBookRepository.findChangedBooks(previousSnapshotId, currentSnapshotId, pageable);
        List<Long> changedBookIds = page.getContent().stream()
                .map(KoboSnapshotBook::getBookId)
                .toList();

        if (!changedBookIds.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(currentSnapshotId, changedBookIds);
        }

        return page;
    }

    private ShelfEntity getKoboShelf(Long userId) {
        return shelfRepository
                .findByUserIdAndName(userId, ShelfType.KOBO.getName())
                .orElseThrow(() -> new NoSuchElementException(
                        String.format("Shelf '%s' not found for user %d", ShelfType.KOBO.getName(), userId)
                ));
    }

    private List<KoboSnapshotBook> mapBooksToKoboSnapshotBook(ShelfEntity shelf, Long userId) {
        return shelf.getBookEntities().stream()
                .filter(book -> isBookOwnedByUser(book, userId))
                .filter(koboCompatibilityService::isBookSupportedForKobo)
                .map(book -> new KoboSnapshotBook(
                        0L,
                        "",
                        book.getId(),
                        book.getPrimaryBookFile().getCurrentHash(),
                        book.getMetadataUpdatedAt(),
                        false))
                .collect(Collectors.toList());
    }

    private boolean isBookOwnedByUser(BookEntity book, Long userId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        if (user.getPermissions().isAdmin()) {
            return true;
        }
        return book.getLibrary()
                .getUsers()
                .stream()
                .map(BookLoreUserEntity::getId)
                .anyMatch(id -> Objects.equals(id, userId));
    }

    public void deleteById(String id) {
        koboLibrarySnapshotRepository.deleteById(id);
    }

}
