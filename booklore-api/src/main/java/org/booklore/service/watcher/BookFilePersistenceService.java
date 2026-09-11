package org.booklore.service.watcher;

import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.PermissionType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.repository.jooq.JooqLibraryPathRepository;
import org.booklore.repository.jooq.dto.LibraryPathRow;
import org.booklore.service.NotificationService;
import org.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static org.booklore.model.enums.PermissionType.ADMIN;
import static org.booklore.model.enums.PermissionType.MANAGE_LIBRARY;

@Slf4j
@Service
@AllArgsConstructor
public class BookFilePersistenceService {

    private final JooqBookEntityRepository jooqBookEntityRepository;
    private final JooqBookRepository jooqBookRepository;
    private final JooqLibraryPathRepository jooqLibraryPathRepository;
    private final NotificationService notificationService;
    private final BookMapper bookMapper;

    @Transactional
    public void updatePathIfChanged(BookEntity book, LibraryEntity libraryEntity, Path path, String currentHash) {
        LibraryPathEntity newLibraryPath = getLibraryPathEntityForFile(libraryEntity, path.toString());

        String newSubPath = FileUtils.getRelativeSubPath(newLibraryPath.getPath(), path);
        String newFileName = path.getFileName().toString();

        BookFileEntity matchedFile = book.getBookFiles().stream()
                .filter(bf -> currentHash.equals(bf.getCurrentHash()))
                .findFirst()
                .orElse(book.getPrimaryBookFile());

        boolean changed = !Objects.equals(newSubPath, matchedFile.getFileSubPath())
                || !Objects.equals(newFileName, matchedFile.getFileName())
                || !Objects.equals(newLibraryPath.getId(), book.getLibraryPath().getId())
                || Boolean.TRUE.equals(book.getDeleted());

        if (changed) {
            book.setLibraryPath(newLibraryPath);
            matchedFile.setFileSubPath(newSubPath);
            matchedFile.setFileName(newFileName);
            book.setDeleted(Boolean.FALSE);
            book.setDeletedAt(null);
            jooqBookRepository.updateLibrary(book.getId(), book.getLibrary().getId(), newLibraryPath.getId());
            jooqBookRepository.updateFileNameAndSubPath(matchedFile.getId(), newFileName, newSubPath);
            jooqBookRepository.markUndeleted(List.of(book.getId()));
            log.info("[FILE_CREATE] Updated path / undeleted existing book with hash '{}': '{}'", currentHash, path);
        } else {
            log.info("[FILE_CREATE] Book with hash '{}' already exists at same path. Skipping update.", currentHash);
        }
        notificationService.sendMessageToPermissions(Topic.BOOK_ADD, bookMapper.toBookWithDescription(book, false), Set.of(ADMIN, MANAGE_LIBRARY));
    }

    /**
     * Reads the library's paths via jOOQ instead of the LAZY libraryEntity.getLibraryPaths(): callers on
     * the file-watcher thread hold a detached LibraryEntity, so touching that collection would throw
     * LazyInitializationException with open-in-view disabled.
     */
    String findMatchingLibraryPath(LibraryEntity libraryEntity, Path filePath) {
        return jooqLibraryPathRepository.findPathsByLibraryId(libraryEntity.getId()).stream()
                .map(lp -> Paths.get(lp.getPath()).toAbsolutePath().normalize())
                .filter(base -> filePath.toAbsolutePath().normalize().startsWith(base))
                .map(Path::toString)
                .findFirst()
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException("No matching libraryPath for: " + filePath));
    }

    /** Same jOOQ-backed lookup as {@link #findMatchingLibraryPath}, resolving the winning row to its entity. */
    LibraryPathEntity getLibraryPathEntityForFile(LibraryEntity libraryEntity, String inputPath) {
        Path fullPath = Paths.get(inputPath).toAbsolutePath().normalize();
        LibraryPathRow match = jooqLibraryPathRepository.findPathsByLibraryId(libraryEntity.getId()).stream()
                .map(lp -> Map.entry(lp, Paths.get(lp.getPath()).toAbsolutePath().normalize()))
                .filter(entry -> fullPath.startsWith(entry.getValue()))
                .max(Comparator.comparingInt(entry -> entry.getValue().getNameCount()))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(inputPath));
        LibraryPathEntity libraryPath = jooqLibraryPathRepository.findById(match.getId());
        if (libraryPath == null) {
            throw ApiError.BOOK_NOT_FOUND.createException(inputPath);
        }
        return libraryPath;
    }

    @Transactional
    public int markAllBooksUnderPathAsDeleted(long libraryPathId, String relativeFolderPath) {
        if (relativeFolderPath == null) {
            throw new IllegalArgumentException("relativeFolderPath cannot be null");
        }
        List<Long> bookIds = jooqBookRepository.findBookIdsByLibraryPathIdAndFileSubPathStartingWith(libraryPathId, relativeFolderPath);
        jooqBookRepository.markDeleted(bookIds, Instant.now());
        return bookIds.size();
    }

    @Transactional(readOnly = true)
    public Optional<BookFileEntity> findBookFileByLibraryPathSubPathAndFileName(long libraryPathId, String fileSubPath, String fileName) {
        return Optional.ofNullable(jooqBookEntityRepository.findBookFileByLibraryPathIdAndFileSubPathAndFileName(libraryPathId, fileSubPath, fileName));
    }

    @Transactional
    public void deleteBookFile(BookFileEntity bookFile) {
        jooqBookRepository.deleteBookFileById(bookFile.getId());
    }

    @Transactional
    public void markBookAsDeleted(BookEntity book) {
        Instant now = Instant.now();
        book.setDeleted(true);
        book.setDeletedAt(now);
        jooqBookRepository.markDeleted(List.of(book.getId()), now);
    }

    @Transactional(readOnly = true)
    public long countBookFilesByBookId(Long bookId) {
        return jooqBookEntityRepository.countBookFilesByBookId(bookId);
    }

    @Transactional
    public void recoverFolderBook(PendingDeletionPool.BookSnapshot bookSnap, LibraryPathEntity newLibraryPath,
                                   Path folderPath, Map<Path, String> fileHashes) {
        BookEntity book = jooqBookEntityRepository.findByIdIncludingDeleted(bookSnap.bookId());
        if (book == null) return;

        book.setLibraryPath(newLibraryPath);
        book.setDeleted(false);
        book.setDeletedAt(null);

        for (PendingDeletionPool.FileSnapshot fs : bookSnap.files()) {
            BookFileEntity bf = jooqBookEntityRepository.findBookFileById(fs.bookFileId());
            if (bf != null) {
                for (var entry : fileHashes.entrySet()) {
                    if (entry.getValue().equals(fs.currentHash())) {
                        Path filePath = entry.getKey();
                        String newSubPath = FileUtils.getRelativeSubPath(newLibraryPath.getPath(), filePath);
                        String newFileName = filePath.getFileName().toString();
                        bf.setFileSubPath(newSubPath);
                        bf.setFileName(newFileName);
                        bf.setCurrentHash(entry.getValue());
                        jooqBookRepository.updateFileNameAndSubPath(bf.getId(), newFileName, newSubPath);
                        jooqBookRepository.updateBookFileHash(bf.getId(), entry.getValue());
                        break;
                    }
                }
            }
        }

        jooqBookRepository.updateLibrary(book.getId(), book.getLibrary().getId(), book.getLibraryPath().getId());
        jooqBookRepository.markUndeleted(List.of(book.getId()));
        notificationService.sendMessageToPermissions(Topic.BOOK_ADD,
                bookMapper.toBookWithDescription(book, false), Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
        log.info("[RECOVERED] Book id={} recovered from folder move", book.getId());
    }
}