package org.booklore.service.migration.migrations;

import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.jooq.JooqUserBookFileProgressRepository;
import org.booklore.repository.jooq.JooqUserBookProgressRepository;
import org.booklore.repository.jooq.dto.UserBookFileProgressRow;
import org.booklore.repository.jooq.dto.UserBookProgressRow;
import org.booklore.service.migration.Migration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MigrateProgressToFileProgressMigration implements Migration {

    private final JooqUserBookProgressRepository userBookProgressRepository;
    private final JooqUserBookFileProgressRepository userBookFileProgressRepository;
    private final BookAdditionalFileRepository bookFileRepository;

    @Override
    public String getKey() {
        return "migrateProgressToFileProgress";
    }

    @Override
    public String getDescription() {
        return "Migrate existing reading progress from user_book_progress to user_book_file_progress";
    }

    @Override
    @Transactional
    public void execute() {
        log.info("Starting migration: {}", getKey());

        List<UserBookProgressRow> allProgress = userBookProgressRepository.findAll();
        int migratedCount = 0;
        int skippedCount = 0;

        for (UserBookProgressRow progress : allProgress) {
            if (!hasAnyProgress(progress)) {
                skippedCount++;
                continue;
            }

            try {
                Optional<BookFileEntity> bookFileOpt = findBookFileForProgress(progress);
                if (bookFileOpt.isEmpty()) {
                    log.debug("No matching book file found for progress id={}, bookId={}",
                            progress.getId(), progress.getBookId());
                    skippedCount++;
                    continue;
                }

                BookFileEntity bookFile = bookFileOpt.get();

                // Check if file progress already exists
                Optional<UserBookFileProgressRow> existingFileProgress =
                        userBookFileProgressRepository.findByUserIdAndBookFileId(
                                progress.getUserId(), bookFile.getId());

                if (existingFileProgress.isPresent()) {
                    log.debug("File progress already exists for userId={}, bookFileId={}",
                            progress.getUserId(), bookFile.getId());
                    skippedCount++;
                    continue;
                }

                UserBookFileProgressRow fileProgress = createFileProgress(progress, bookFile);
                userBookFileProgressRepository.save(fileProgress);
                migratedCount++;
            } catch (Exception e) {
                log.warn("Failed to migrate progress for progressId={}: {}",
                        progress.getId(), e.getMessage());
                skippedCount++;
            }
        }

        log.info("Migration '{}' completed. Migrated: {}, Skipped: {}",
                getKey(), migratedCount, skippedCount);
    }

    private boolean hasAnyProgress(UserBookProgressRow progress) {
        return progress.getPdfProgress() != null ||
                progress.getEpubProgress() != null ||
                progress.getCbxProgress() != null;
    }

    private Optional<BookFileEntity> findBookFileForProgress(UserBookProgressRow progress) {
        if (progress.getBookId() == null) {
            return Optional.empty();
        }

        List<BookFileEntity> bookFiles = bookFileRepository.findByBookId(progress.getBookId());
        if (bookFiles == null || bookFiles.isEmpty()) {
            return Optional.empty();
        }

        // Determine which type of progress we have and find matching book file
        if (progress.getPdfProgress() != null) {
            return findBookFileByType(bookFiles, BookFileType.PDF);
        } else if (progress.getEpubProgress() != null) {
            // EPUB progress can apply to EPUB, FB2, MOBI, AZW3
            return findBookFileByTypes(bookFiles, BookFileType.EPUB, BookFileType.FB2,
                    BookFileType.MOBI, BookFileType.AZW3);
        } else if (progress.getCbxProgress() != null) {
            return findBookFileByType(bookFiles, BookFileType.CBX);
        }

        return Optional.empty();
    }

    private Optional<BookFileEntity> findBookFileByType(List<BookFileEntity> bookFiles, BookFileType type) {
        return bookFiles.stream()
                .filter(bf -> bf.isBookFormat() && bf.getBookType() == type)
                .findFirst();
    }

    private Optional<BookFileEntity> findBookFileByTypes(List<BookFileEntity> bookFiles, BookFileType... types) {
        for (BookFileType type : types) {
            Optional<BookFileEntity> found = findBookFileByType(bookFiles, type);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private UserBookFileProgressRow createFileProgress(UserBookProgressRow progress, BookFileEntity bookFile) {
        UserBookFileProgressRow fileProgress = new UserBookFileProgressRow();
        fileProgress.setUserId(progress.getUserId());
        fileProgress.setBookFileId(bookFile.getId());
        fileProgress.setLastReadTime(progress.getLastReadTime());

        // Map progress data based on book type
        switch (bookFile.getBookType()) {
            case PDF -> {
                fileProgress.setPositionData(progress.getPdfProgress() != null ?
                        String.valueOf(progress.getPdfProgress()) : null);
                fileProgress.setProgressPercent(progress.getPdfProgressPercent());
            }
            case EPUB, FB2, MOBI, AZW3 -> {
                fileProgress.setPositionData(progress.getEpubProgress());
                fileProgress.setPositionHref(progress.getEpubProgressHref());
                fileProgress.setProgressPercent(progress.getEpubProgressPercent());
            }
            case CBX -> {
                fileProgress.setPositionData(progress.getCbxProgress() != null ?
                        String.valueOf(progress.getCbxProgress()) : null);
                fileProgress.setProgressPercent(progress.getCbxProgressPercent());
            }
        }

        return fileProgress;
    }
}
