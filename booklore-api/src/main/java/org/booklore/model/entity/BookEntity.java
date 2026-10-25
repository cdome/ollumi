package org.booklore.model.entity;

import lombok.*;
import org.booklore.model.dto.BookRecommendationLite;
import org.booklore.model.enums.BookFileType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookEntity {

    private Long id;

    private Float metadataMatchScore;

    private BookMetadataEntity metadata;

    private Instant metadataUpdatedAt;

    private Instant metadataForWriteUpdatedAt;

    private LibraryEntity library;

    private LibraryPathEntity libraryPath;

    @Builder.Default
    private Boolean isPhysical = Boolean.FALSE;

    private Instant addedOn;

    private Instant scannedOn;

    private String bookCoverHash;

    private String audiobookCoverHash;

    @Builder.Default
    private Boolean deleted = Boolean.FALSE;

    private Instant deletedAt;

    private Set<BookRecommendationLite> similarBooksJson;

    @Builder.Default
    private List<BookFileEntity> bookFiles = new ArrayList<>();

    public Path getFullFilePath() {
        BookFileEntity primaryBookFile = getPrimaryBookFile();
        if (primaryBookFile == null || libraryPath == null || libraryPath.getPath() == null || primaryBookFile.getFileSubPath() == null || primaryBookFile.getFileName() == null) {
            return null;
        }

        return Paths.get(libraryPath.getPath(), primaryBookFile.getFileSubPath(), primaryBookFile.getFileName());
    }

    public BookFileEntity getPrimaryBookFile() {
        if (bookFiles == null) {
            bookFiles = new ArrayList<>();
        }
        if (bookFiles.isEmpty()) {
            return null;
        }
        if (library != null && library.getFormatPriority() != null && !library.getFormatPriority().isEmpty()) {
            for (BookFileType format : library.getFormatPriority()) {
                var match = bookFiles.stream()
                        .filter(bf -> bf.isBookFormat() && bf.getBookType() == format)
                        .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
        }
        return bookFiles.getFirst();
    }

    public boolean hasFiles() {
        return bookFiles != null && !bookFiles.isEmpty();
    }
}
