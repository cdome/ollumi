package org.booklore.service.library;

import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookRestorationService {

    private final JooqBookEntityRepository jooqBookEntityRepository;
    private final JooqBookRepository jooqBookRepository;
    private final BookMapper bookMapper;
    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreDeletedBooks(List<LibraryFile> libraryFiles) {
        if (libraryFiles.isEmpty()) return;

        LibraryEntity libraryEntity = libraryFiles.getFirst().getLibraryEntity();
        Set<Path> currentPaths = libraryFiles.stream()
                .map(LibraryFile::getFullPath)
                .collect(Collectors.toSet());

        List<BookEntity> toRestore = jooqBookEntityRepository.findDeletedByLibraryId(libraryEntity.getId()).stream()
                .filter(book -> book.getBookFiles() != null && !book.getBookFiles().isEmpty())
                .filter(book -> currentPaths.contains(book.getFullFilePath()))
                .collect(Collectors.toList());

        if (toRestore.isEmpty()) return;

        Instant now = Instant.now();
        toRestore.forEach(book -> {
            book.setDeleted(false);
            book.setDeletedAt(null);
            book.setAddedOn(now);
            notificationService.sendMessage(Topic.BOOK_ADD, bookMapper.toBookWithDescription(book, false));
        });

        List<Long> restoredIds = toRestore.stream()
                .map(BookEntity::getId)
                .toList();
        jooqBookRepository.restoreDeleted(restoredIds, now);

        log.info("Restored {} books in library: {}", restoredIds.size(), libraryEntity.getName());
    }
}
