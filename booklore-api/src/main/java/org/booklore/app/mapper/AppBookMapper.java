package org.booklore.app.mapper;

import org.booklore.app.dto.AppBookDetail;
import org.booklore.app.dto.AppBookFile;
import org.booklore.app.dto.AppLibrarySummary;
import org.booklore.app.dto.AppMagicShelfSummary;
import org.booklore.app.dto.AppShelfSummary;
import org.booklore.model.entity.*;
import org.booklore.repository.jooq.dto.LibraryPathRow;
import org.booklore.repository.jooq.dto.MagicShelfRow;
import org.booklore.repository.jooq.dto.UserBookFileProgressRow;
import org.booklore.repository.jooq.dto.UserBookProgressRow;
import org.booklore.model.enums.BookFileType;
import org.mapstruct.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AppBookMapper {

    // toSummary/toDetail were superseded by the jOOQ read model (JooqAppBookSummaryRepository /
    // JooqAppBookDetailRepository, Phase 9) and had zero remaining callers; deleted along with
    // mapCategories, which existed only to support toDetail's categories mapping and referenced the
    // now-removed BookMetadataEntity.categories Hibernate collection via CategoryEntity.

    @Named("mapThumbnailUrl")
    default String mapThumbnailUrl(BookEntity book) {
        if (book == null || book.getId() == null) {
            return null;
        }
        return "/api/books/" + book.getId() + "/cover";
    }

    @Named("mapShelves")
    default List<AppShelfSummary> mapShelves(Set<ShelfEntity> shelves) {
        if (shelves == null || shelves.isEmpty()) {
            return Collections.emptyList();
        }
        return shelves.stream()
                .map(this::toShelfSummary)
                .collect(Collectors.toList());
    }

    default AppShelfSummary toShelfSummary(ShelfEntity shelf) {
        if (shelf == null) {
            return null;
        }
        return AppShelfSummary.builder()
                .id(shelf.getId())
                .name(shelf.getName())
                .icon(shelf.getIcon())
                .bookCount(shelf.getBookCount())
                .publicShelf(shelf.isPublic())
                .build();
    }

    @Named("mapReadProgress")
    default Float mapReadProgress(UserBookProgressRow progress) {
        if (progress == null) {
            return null;
        }
        if (progress.getKoreaderProgressPercent() != null) {
            return progress.getKoreaderProgressPercent();
        }
        if (progress.getKoboProgressPercent() != null) {
            return progress.getKoboProgressPercent();
        }
        if (progress.getEpubProgressPercent() != null) {
            return progress.getEpubProgressPercent();
        }
        if (progress.getPdfProgressPercent() != null) {
            return progress.getPdfProgressPercent();
        }
        if (progress.getCbxProgressPercent() != null) {
            return progress.getCbxProgressPercent();
        }
        return null;
    }

    @Named("mapEpubProgress")
    default AppBookDetail.EpubProgress mapEpubProgress(UserBookProgressRow progress) {
        if (progress == null || progress.getEpubProgress() == null) {
            return null;
        }
        return AppBookDetail.EpubProgress.builder()
                .cfi(progress.getEpubProgress())
                .href(progress.getEpubProgressHref())
                .percentage(progress.getEpubProgressPercent())
                .updatedAt(progress.getLastReadTime())
                .build();
    }

    @Named("mapPdfProgress")
    default AppBookDetail.PdfProgress mapPdfProgress(UserBookProgressRow progress) {
        if (progress == null || progress.getPdfProgress() == null) {
            return null;
        }
        return AppBookDetail.PdfProgress.builder()
                .page(progress.getPdfProgress())
                .percentage(progress.getPdfProgressPercent())
                .updatedAt(progress.getLastReadTime())
                .build();
    }

    @Named("mapCbxProgress")
    default AppBookDetail.CbxProgress mapCbxProgress(UserBookProgressRow progress) {
        if (progress == null || progress.getCbxProgress() == null) {
            return null;
        }
        return AppBookDetail.CbxProgress.builder()
                .page(progress.getCbxProgress())
                .percentage(progress.getCbxProgressPercent())
                .updatedAt(progress.getLastReadTime())
                .build();
    }

    @Named("mapKoreaderProgress")
    default AppBookDetail.KoreaderProgress mapKoreaderProgress(UserBookProgressRow progress) {
        if (progress == null || progress.getKoreaderProgressPercent() == null) {
            return null;
        }
        return AppBookDetail.KoreaderProgress.builder()
                .percentage(progress.getKoreaderProgressPercent())
                .device(progress.getKoreaderDevice())
                .deviceId(progress.getKoreaderDeviceId())
                .lastSyncTime(progress.getKoreaderLastSyncTime())
                .build();
    }

    @Named("mapAudiobookProgress")
    default AppBookDetail.AudiobookProgress mapAudiobookProgress(UserBookFileProgressRow fileProgress) {
        if (fileProgress == null) return null;
        if (fileProgress.getBookType() != BookFileType.AUDIOBOOK) {
            return null;
        }

        return AppBookDetail.AudiobookProgress.builder()
                .positionMs(parseLongOrNull(fileProgress.getPositionData()))
                .trackIndex(parseIntOrNull(fileProgress.getPositionHref()))
                .percentage(fileProgress.getProgressPercent())
                .updatedAt(fileProgress.getLastReadTime())
                .build();
    }

    default Long parseLongOrNull(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    default Integer parseIntOrNull(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Named("mapPrimaryFileType")
    default String mapPrimaryFileType(BookEntity book) {
        if (book == null) {
            return null;
        }
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        if (primaryFile != null && primaryFile.getBookType() != null) {
            return primaryFile.getBookType().name();
        }
        return null;
    }

    @Named("mapFileTypes")
    default List<String> mapFileTypes(BookEntity book) {
        if (book == null || book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
            return Collections.emptyList();
        }
        return book.getBookFiles().stream()
                .filter(bf -> bf.getBookType() != null)
                .map(bf -> bf.getBookType().name())
                .distinct()
                .collect(Collectors.toList());
    }

    @Named("mapFiles")
    default List<AppBookFile> mapFiles(BookEntity book) {
        if (book == null || book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
            return Collections.emptyList();
        }
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        Long primaryId = primaryFile != null ? primaryFile.getId() : null;

        return book.getBookFiles().stream()
                .filter(bf -> bf.getBookType() != null && bf.isBook())
                .map(bf -> {
                    String extension = null;
                    try {
                        String fileName = bf.getFileName();
                        int lastDot = fileName.lastIndexOf('.');
                        if (lastDot > 0) {
                            extension = fileName.substring(lastDot + 1);
                        }
                    } catch (Exception e) {
                        // Handle case where extension cannot be extracted
                    }

                    return AppBookFile.builder()
                            .id(bf.getId())
                            .bookId(bf.getBook() != null ? bf.getBook().getId() : null)
                            .fileName(bf.getFileName())
                            .isBook(bf.isBook())
                            .folderBased(bf.isFolderBased())
                            .bookType(bf.getBookType().name())
                            .archiveType(bf.getArchiveType() != null ? bf.getArchiveType().name() : null)
                            .fileSizeKb(bf.getFileSizeKb())
                            .extension(extension)
                            .addedOn(bf.getAddedOn())
                            .isPrimary(bf.getId().equals(primaryId))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Paths are passed in (read via jOOQ by the caller) rather than taken from the LAZY
     * library.getLibraryPaths(): this runs outside a transaction, where that collection would throw.
     */
    default AppLibrarySummary toLibrarySummary(LibraryEntity library, long bookCount, List<LibraryPathRow> pathRows) {
        if (library == null) {
            return null;
        }
        List<AppLibrarySummary.PathSummary> paths = pathRows == null ? Collections.emptyList()
                : pathRows.stream()
                    .map(lp -> AppLibrarySummary.PathSummary.builder()
                            .id(lp.getId())
                            .path(lp.getPath())
                            .build())
                    .collect(Collectors.toList());
        return AppLibrarySummary.builder()
                .id(library.getId())
                .name(library.getName())
                .icon(library.getIcon())
                .bookCount(bookCount)
                .allowedFormats(library.getAllowedFormats())
                .paths(paths)
                .build();
    }

    default AppShelfSummary toShelfSummaryFromEntity(ShelfEntity shelf) {
        if (shelf == null) {
            return null;
        }
        return AppShelfSummary.builder()
                .id(shelf.getId())
                .name(shelf.getName())
                .icon(shelf.getIcon())
                .bookCount(shelf.getBookCount())
                .publicShelf(shelf.isPublic())
                .build();
    }

    default AppMagicShelfSummary toMagicShelfSummary(MagicShelfRow magicShelf) {
        if (magicShelf == null) {
            return null;
        }
        return AppMagicShelfSummary.builder()
                .id(magicShelf.getId())
                .name(magicShelf.getName())
                .icon(magicShelf.getIcon())
                .iconType(magicShelf.getIconType() != null ? magicShelf.getIconType().name() : null)
                .publicShelf(magicShelf.isPublic())
                .build();
    }
}
