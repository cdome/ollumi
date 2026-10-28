package org.booklore.service.reader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.AudiobookMetadata;
import org.booklore.model.dto.response.AudiobookChapter;
import org.booklore.model.dto.response.AudiobookInfo;
import org.booklore.model.dto.response.AudiobookTrack;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.jooq.JooqBookMetadataRelationsRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.service.metadata.extractor.AudiobookMetadataExtractor;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AudioMetadataService {

  private final AudioFileUtilityService audioFileUtility;
  private final AudiobookMetadataExtractor audiobookMetadataExtractor;
  private final JooqBookRepository jooqBookRepository;
  private final JooqBookMetadataRelationsRepository bookMetadataRelationsRepository;

  public AudiobookInfo getMetadata(BookFileEntity bookFile, Path audioPath) throws Exception {
    if (bookFile.isFolderBased()) {
      return buildFolderBasedAudiobookInfo(bookFile, audioPath);
    } else {
      return buildSingleFileAudiobookInfo(bookFile, audioPath);
    }
  }

  private AudiobookInfo buildSingleFileAudiobookInfo(BookFileEntity bookFile, Path audioPath)
      throws Exception {
    Long bookId = bookFile.getBook().getId();
    Long bookFileId = bookFile.getId();
    Long totalSizeBytes = bookFile.getFileSizeKb() != null ? bookFile.getFileSizeKb() * 1024 : Files.size(audioPath);

    if (bookFile.getDurationSeconds() != null) {
      BookMetadataEntity metadata = bookFile.getBook().getMetadata();
      String narrator = metadata != null ? metadata.getNarrator() : null;
      Long durationMs = bookFile.getDurationSeconds() * 1000;
      Integer bitrate = bookFile.getBitrate();
      Integer sampleRate = bookFile.getSampleRate();
      Integer channels = bookFile.getChannels();
      String codec = bookFile.getCodec();

      List<AudiobookChapter> chapters;
      if (bookFile.getChapters() != null && !bookFile.getChapters().isEmpty()) {
        chapters =
            bookFile.getChapters().stream()
                .map(
                    ch ->
                        new AudiobookChapter(ch.getIndex(), ch.getTitle(), ch.getStartTimeMs(), ch.getEndTimeMs(), ch.getDurationMs()))
                .collect(Collectors.toList());
      } else {
        chapters = backfillChapters(bookFile, audioPath);
      }

      String title = null;
      String author = null;
      if (metadata != null) {
        title = metadata.getTitle();
        author = bookMetadataRelationsRepository.findAuthorNamesByBookId(bookFile.getBook().getId())
            .stream().findFirst().orElse(null);
      }

      return new AudiobookInfo(bookId, bookFileId, title, author, narrator, durationMs, bitrate, codec, sampleRate, channels, totalSizeBytes, false, chapters, null);
    }

    log.debug("No DB metadata found for audiobook {}, extracting from file", bookFile.getId());
    return extractSingleFileMetadata(bookId, bookFileId, totalSizeBytes, audioPath);
  }

  private AudiobookInfo buildFolderBasedAudiobookInfo(BookFileEntity bookFile, Path folderPath)
      throws Exception {
    Long bookId = bookFile.getBook().getId();
    Long bookFileId = bookFile.getId();

    List<Path> audioFiles = audioFileUtility.listAudioFiles(folderPath);
    if (audioFiles.isEmpty()) {
      throw new IllegalStateException("No audio files found in folder: " + folderPath);
    }

    List<AudiobookTrack> tracks = new ArrayList<>();
    long totalDurationMs = 0;
    String title = null;
    String author = null;
    String narrator = null;
    Integer bitrate = null;
    String codec = null;
    Integer sampleRate = null;
    Integer channels = null;

    for (int i = 0; i < audioFiles.size(); i++) {
      Path trackPath = audioFiles.get(i);
      try {
        AudioFile audioFile = AudioFileIO.read(trackPath.toFile());
        AudioHeader header = audioFile.getAudioHeader();
        Tag tag = audioFile.getTag();

        long trackDurationMs = (long) (header.getPreciseTrackLength() * 1000);
        long fileSizeBytes = Files.size(trackPath);

        String trackTitle = null;
        if (tag != null) {
          trackTitle = tag.getFirst(FieldKey.TITLE);
        }
        if (trackTitle == null || trackTitle.isEmpty()) {
          trackTitle =
              audioFileUtility.getTrackTitleFromFilename(trackPath.getFileName().toString());
        }

        tracks.add(
            new AudiobookTrack(i, trackPath.getFileName().toString(), trackTitle, trackDurationMs, fileSizeBytes, totalDurationMs));

        totalDurationMs += trackDurationMs;

        if (i == 0) {
          bitrate = safeBitrate(header, trackPath);
          codec = safeEncodingType(header, trackPath);
          sampleRate = safeSampleRate(header, trackPath);
          channels = parseChannels(safeChannels(header, trackPath));
          if (tag != null) {
            title = getTagValue(tag, FieldKey.ALBUM, FieldKey.TITLE);
            author = getTagValue(tag, FieldKey.ALBUM_ARTIST, FieldKey.ARTIST);
            narrator = getTagValue(tag, FieldKey.COMPOSER);
          }
        }
      } catch (Exception e) {
        log.warn("Failed to read track metadata: {}", trackPath, e);
        tracks.add(
            new AudiobookTrack(i, trackPath.getFileName().toString(), audioFileUtility.getTrackTitleFromFilename(trackPath.getFileName().toString()), null, Files.size(trackPath), totalDurationMs));
      }
    }

    BookMetadataEntity metadata = bookFile.getBook().getMetadata();
    String finalNarrator = (metadata != null && metadata.getNarrator() != null)
        ? metadata.getNarrator()
        : narrator;
    Integer finalBitrate = bookFile.getBitrate() != null ? bookFile.getBitrate() : bitrate;
    String finalCodec = bookFile.getCodec() != null ? bookFile.getCodec() : codec;
    Integer finalSampleRate = bookFile.getSampleRate() != null ? bookFile.getSampleRate() : sampleRate;
    Integer finalChannels = bookFile.getChannels() != null ? bookFile.getChannels() : channels;

    if (metadata != null) {
      String bookTitle = metadata.getTitle();
      if (bookTitle != null) {
        title = bookTitle;
      }
      author = bookMetadataRelationsRepository.findAuthorNamesByBookId(bookFile.getBook().getId())
          .stream().findFirst().orElse(author);
    }

    long totalSizeBytes = tracks.stream()
                .mapToLong(t -> t.getFileSizeBytes() != null ? t.getFileSizeBytes() : 0)
                .sum();
    Long totalSizeBytesObj = totalSizeBytes > 0 ? totalSizeBytes : null;

    return new AudiobookInfo(bookId, bookFileId, title, author, finalNarrator, totalDurationMs, finalBitrate, finalCodec, finalSampleRate, finalChannels, totalSizeBytesObj, true, null, tracks);
  }

  private AudiobookInfo extractSingleFileMetadata(
      Long bookId, Long bookFileId, Long totalSizeBytes, Path audioPath) throws Exception {
    AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
    AudioHeader header = audioFile.getAudioHeader();
    Tag tag = audioFile.getTag();

    long durationMs = safeDurationMs(header, audioPath);
    Integer bitrate = safeBitrate(header, audioPath);
    String codec = safeEncodingType(header, audioPath);
    Integer sampleRate = safeSampleRate(header, audioPath);
    Integer channels = parseChannels(safeChannels(header, audioPath));

    String title = null;
    String author = null;
    String narrator = null;
    if (tag != null) {
      title = getTagValue(tag, FieldKey.TITLE, FieldKey.ALBUM);
      author = getTagValue(tag, FieldKey.ARTIST, FieldKey.ALBUM_ARTIST);
      narrator = getTagValue(tag, FieldKey.COMPOSER);
    }

    List<AudiobookChapter> chapters = extractChaptersFromFile(audioPath.toFile(), durationMs);

    return new AudiobookInfo(bookId, bookFileId, title, author, narrator, durationMs, bitrate, codec, sampleRate, channels, totalSizeBytes, false, chapters, null);
  }

  private List<AudiobookChapter> backfillChapters(
      BookFileEntity bookFile, Path audioPath) {
    try {
      List<AudiobookChapter> chapters =
          extractChaptersFromFile(
              audioPath.toFile(),
              bookFile.getDurationSeconds() != null ? bookFile.getDurationSeconds() * 1000 : 0);

      List<BookFileEntity.AudioFileChapter> entityChapters =
          chapters.stream()
              .map(
                  ch ->
                      BookFileEntity.AudioFileChapter.builder()
                          .index(ch.getIndex())
                          .title(ch.getTitle())
                          .startTimeMs(ch.getStartTimeMs())
                          .endTimeMs(ch.getEndTimeMs())
                          .durationMs(ch.getDurationMs())
                          .build())
              .collect(Collectors.toList());
      bookFile.setChapters(entityChapters);
      bookFile.setChapterCount(entityChapters.size());
      jooqBookRepository.updateBookFileFullRow(bookFile);
      log.info(
          "Backfilled {} chapters for audiobook file id={}",
          entityChapters.size(),
          bookFile.getId());
      return chapters;
    } catch (Exception e) {
      log.debug(
          "Failed to backfill chapters for audiobook file id={}: {}",
          bookFile.getId(),
          e.getMessage());
      return new ArrayList<>();
    }
  }

  private List<AudiobookChapter> extractChaptersFromFile(File audioFile, long fallbackDurationMs) {
    List<AudiobookMetadata.ChapterInfo> extracted =
        audiobookMetadataExtractor.extractChaptersFromFile(audioFile);
    if (extracted != null && !extracted.isEmpty()) {
      return extracted.stream()
          .map(
              ch ->
                  new AudiobookChapter(ch.getIndex(), ch.getTitle(), ch.getStartTimeMs(), ch.getEndTimeMs(), ch.getDurationMs()))
          .collect(Collectors.toList());
    }

    List<AudiobookChapter> fallback = new ArrayList<>();
    fallback.add(
        new AudiobookChapter(0, "Full Audiobook", 0L, fallbackDurationMs, fallbackDurationMs));
    return fallback;
  }

  public byte[] getEmbeddedCoverArt(Path audioPath) {
    try {
      AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
      Tag tag = audioFile.getTag();
      if (tag != null) {
        Artwork artwork = tag.getFirstArtwork();
        if (artwork != null) {
          return artwork.getBinaryData();
        }
      }
    } catch (Exception e) {
      log.debug("No embedded cover art found: {}", e.getMessage());
    }
    return null;
  }

  public String getCoverArtMimeType(Path audioPath) {
    try {
      AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
      Tag tag = audioFile.getTag();
      if (tag != null) {
        Artwork artwork = tag.getFirstArtwork();
        if (artwork != null) {
          String mimeType = artwork.getMimeType();
          if (mimeType != null && !mimeType.isEmpty()) {
            return mimeType;
          }
          byte[] data = artwork.getBinaryData();
          if (data != null && data.length > 2) {
            if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
              return "image/jpeg";
            } else if (data[0] == (byte) 0x89 && data[1] == (byte) 0x50) {
              return "image/png";
            }
          }
          return "image/jpeg";
        }
      }
    } catch (Exception e) {
      log.debug("Could not determine cover art MIME type: {}", e.getMessage());
    }
    return "image/jpeg";
  }

  private String getTagValue(Tag tag, FieldKey... keys) {
    for (FieldKey key : keys) {
      try {
        String value = tag.getFirst(key);
        if (value != null && !value.isEmpty()) {
          return value;
        }
      } catch (Exception e) {
      }
    }
    return null;
  }

  private Integer parseChannels(String channels) {
    if (channels == null) return null;
    if (channels.toLowerCase().contains("stereo")) return 2;
    if (channels.toLowerCase().contains("mono")) return 1;
    try {
      return Integer.parseInt(channels.replaceAll("[^0-9]", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

    private long safeDurationMs(AudioHeader header, Path audioPath) {
        try {
            return (long) (header.getPreciseTrackLength() * 1000);
        } catch (RuntimeException e) {
            log.warn("Failed to read track duration from {}", audioPath, e);
            return 0L;
        }
    }

    private Integer safeBitrate(AudioHeader header, Path audioPath) {
        try {
            long bitrateValue = header.getBitRateAsNumber();
            return bitrateValue > 0 ? (int) bitrateValue : null;
        } catch (RuntimeException e) {
            log.debug("Audio header has no bitrate for {}", audioPath, e);
            return null;
        }
    }

    private Integer safeSampleRate(AudioHeader header, Path audioPath) {
        try {
            int sampleRate = header.getSampleRateAsNumber();
            return sampleRate > 0 ? sampleRate : null;
        } catch (RuntimeException e) {
            log.debug("Audio header has no sample rate for {}", audioPath, e);
            return null;
        }
    }

    private String safeEncodingType(AudioHeader header, Path audioPath) {
        try {
            String encodingType = header.getEncodingType();
            return encodingType != null && !encodingType.isBlank() ? encodingType : null;
        } catch (RuntimeException e) {
            log.debug("Audio header has no encoding type for {}", audioPath, e);
            return null;
        }
    }

    private String safeChannels(AudioHeader header, Path audioPath) {
        try {
            return header.getChannels();
        } catch (RuntimeException e) {
            log.debug("Audio header has no channel info for {}", audioPath, e);
            return null;
        }
    }
}
