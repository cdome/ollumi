package org.booklore.service.bookdrop;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.MetadataRefreshOptions;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.repository.jooq.JooqBookdropFileRepository;
import org.booklore.repository.jooq.dto.BookdropFileRow;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.MetadataRefreshService;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.util.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.booklore.model.enums.BookdropFileStatus.PENDING_REVIEW;

@Slf4j
@AllArgsConstructor
@Service
public class BookdropMetadataService {

    private final JooqBookdropFileRepository bookdropFileRepository;
    private final AppSettingService appSettingService;
    private final ObjectMapper objectMapper;
    private final MetadataExtractorFactory metadataExtractorFactory;
    private final MetadataRefreshService metadataRefreshService;
    private final FileService fileService;

    @Transactional
    public BookdropFileRow attachInitialMetadata(Long bookdropFileId) throws JacksonException {
        BookdropFileRow entity = getOrThrow(bookdropFileId);
        BookMetadata initial = extractInitialMetadata(entity);
        if (initial == null) {
            log.warn("Metadata extraction returned null for file: {}. Using filename as fallback.", entity.getFileName());
            initial = BookMetadata.builder()
                    .title(FilenameUtils.getBaseName(entity.getFileName()))
                    .build();
        }
        extractAndSaveCover(entity);
        String initialJson = objectMapper.writeValueAsString(initial);
        return bookdropFileRepository.updateOriginalMetadata(bookdropFileId, initialJson);
    }

    @Transactional
    public BookdropFileRow attachFetchedMetadata(Long bookdropFileId) throws JacksonException {
        BookdropFileRow entity = getOrThrow(bookdropFileId);

        AppSettings appSettings = appSettingService.getAppSettings();

        MetadataRefreshOptions refreshOptions = appSettings.getDefaultMetadataRefreshOptions();

        BookMetadata initial = objectMapper.readValue(entity.getOriginalMetadata(), BookMetadata.class);

        if (!hasSearchableMetadata(initial, entity)) {
            log.info("Skipping online metadata fetch for '{}' — no reliable search data (title derived from filename, no ISBN or ASIN).", entity.getFileName());
            return bookdropFileRepository.updateStatus(bookdropFileId, PENDING_REVIEW);
        }

        List<MetadataProvider> providers = metadataRefreshService.prepareProviders(refreshOptions);
        Book book = Book.builder()
                .metadata(initial)
                .build();

        if (providers.contains(MetadataProvider.GoodReads)) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(250, 1250));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        Map<MetadataProvider, BookMetadata> metadataMap = metadataRefreshService.fetchMetadataForBook(providers, book);
        BookMetadata fetchedMetadata = metadataRefreshService.buildFetchMetadata(initial, book.getId(), refreshOptions, metadataMap);
        String fetchedJson = objectMapper.writeValueAsString(fetchedMetadata);

        return bookdropFileRepository.updateFetchedMetadataAndStatus(bookdropFileId, fetchedJson, PENDING_REVIEW);
    }

    private boolean hasSearchableMetadata(BookMetadata metadata, BookdropFileRow entity) {
        if (hasAnyKnownIdentifier(metadata)) {
            return true;
        }
        String title = metadata.getTitle();
        String filenameFallback = FilenameUtils.getBaseName(entity.getFileName());
        return title != null && !title.isBlank()
                && !title.strip().equalsIgnoreCase(filenameFallback.strip());
    }

    private boolean hasAnyKnownIdentifier(BookMetadata m) {
        // Keep in sync with identifier fields in BookMetadata when new providers are added.
        return Stream.of(
                m.getIsbn13(), m.getIsbn10(), m.getAsin(),
                m.getGoodreadsId(), m.getGoogleId(),
                m.getHardcoverId(), m.getHardcoverBookId(),
                m.getComicvineId(), m.getDoubanId(),
                m.getLubimyczytacId(), m.getRanobedbId(), m.getAudibleId()
        ).anyMatch(id -> id != null && !id.isBlank());
    }

    private BookdropFileRow getOrThrow(Long id) {
        return bookdropFileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Bookdrop file not found: " + id));
    }

    private BookMetadata extractInitialMetadata(BookdropFileRow entity) {
        File file = new File(entity.getFilePath());
        BookFileExtension fileExt = BookFileExtension.fromFileName(file.getName())
            .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"));
        return metadataExtractorFactory.extractMetadata(fileExt, file);
    }

    private void extractAndSaveCover(BookdropFileRow entity) {
        File file = new File(entity.getFilePath());
        BookFileExtension fileExt = BookFileExtension.fromFileName(file.getName())
            .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"));
        byte[] coverBytes = metadataExtractorFactory.extractCover(fileExt, file);
        if (coverBytes != null) {
            try {
                FileService.saveImage(coverBytes, fileService.getTempBookdropCoverImagePath(entity.getId()));
            } catch (IOException e) {
                log.warn("Failed to save extracted cover for file: {}", entity.getFilePath(), e);
            }
        }
    }
}
