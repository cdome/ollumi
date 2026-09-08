package org.booklore.service.migration.migrations;

import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.migration.Migration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopulateFileHashesMigration implements Migration {

    private final BookRepository bookRepository;
    private final JooqBookRepository jooqBookRepository;

    @Override
    public String getKey() {
        return "populateFileHashesV2";
    }

    @Override
    public String getDescription() {
        return "Calculate and store initialHash and currentHash for all books";
    }

    @Override
    public void execute() {
        log.info("Starting migration: {}", getKey());

        List<BookEntity> books = bookRepository.findAll();
        int updated = 0;

        for (BookEntity book : books) {
            Path path = book.getFullFilePath();
            if (path == null || !Files.exists(path)) {
                log.warn("Skipping hashing for book ID {} — file not found at path: {}", book.getId(), path);
                continue;
            }

            try {
                String hash = FileFingerprint.generateHash(path);
                Long fileId = book.getPrimaryBookFile().getId();
                if (book.getPrimaryBookFile().getInitialHash() == null) {
                    book.getPrimaryBookFile().setInitialHash(hash);
                    jooqBookRepository.updateInitialHashIfNull(fileId, hash);
                }
                book.getPrimaryBookFile().setCurrentHash(hash);
                jooqBookRepository.updateBookFileHash(fileId, hash);
                updated++;
            } catch (Exception e) {
                log.error("Failed to compute hash for file: {}", path, e);
            }
        }

        log.info("Migration '{}' applied to {} books.", getKey(), updated);
    }
}

