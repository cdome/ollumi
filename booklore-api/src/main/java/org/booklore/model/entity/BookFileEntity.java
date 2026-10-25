package org.booklore.model.entity;

import lombok.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.util.ArchiveUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookFileEntity {

    private Long id;

    private BookEntity book;

    private String fileName;

    private String fileSubPath;

    private boolean isBookFormat;

    @Builder.Default
    private boolean folderBased = false;

    private BookFileType bookType;

    @Builder.Default
    private boolean isFixedLayout = false;

    private ArchiveUtils.ArchiveType archiveType;

    private Long fileSizeKb;

    private String initialHash;

    private String currentHash;

    private String altFormatCurrentHash;

    private String description;

    private Instant addedOn;

    private Long durationSeconds;

    private Integer bitrate;

    private Integer sampleRate;

    private Integer channels;

    private String codec;

    private Integer chapterCount;

    private List<AudioFileChapter> chapters;

    public boolean isBook() {
        return isBookFormat;
    }

    public boolean isFixedLayout() {
        return isFixedLayout;
    }

    public Path getFullFilePath() {
        if (book == null || book.getLibraryPath() == null || book.getLibraryPath().getPath() == null
                || fileSubPath == null || fileName == null) {
            throw new IllegalStateException("Cannot construct file path: missing book, library path, file subpath, or file name");
        }

        return Paths.get(book.getLibraryPath().getPath(), fileSubPath, fileName);
    }

    /**
     * For folder-based audiobooks, returns the folder path.
     * For regular files, returns the file path.
     */
    public Path getAudiobookPath() {
        Path fullPath = getFullFilePath();
        return folderBased ? fullPath : fullPath;
    }

    /**
     * For folder-based audiobooks, returns the first audio file in the folder (for metadata extraction).
     * For regular files, returns the file itself.
     */
    public Path getFirstAudioFile() {
        if (!folderBased) {
            return getFullFilePath();
        }
        Path folderPath = getFullFilePath();
        try (var files = Files.list(folderPath)) {
            return files
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".m4b") || name.endsWith(".opus");
                    })
                    .sorted()
                    .findFirst()
                    .orElse(folderPath);
        } catch (IOException e) {
            return folderPath;
        }
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioFileChapter {
        private Integer index;
        private String title;
        private Long startTimeMs;
        private Long endTimeMs;
        private Long durationMs;
    }
}
