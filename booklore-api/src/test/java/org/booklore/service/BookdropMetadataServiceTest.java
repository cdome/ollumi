package org.booklore.service;

import org.booklore.exception.APIException;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.repository.jooq.JooqBookdropFileRepository;
import org.booklore.repository.jooq.dto.BookdropFileRow;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.bookdrop.BookdropMetadataService;
import org.booklore.service.metadata.MetadataRefreshService;
import org.booklore.service.metadata.extractor.CbxMetadataExtractor;
import org.booklore.service.metadata.extractor.EpubMetadataExtractor;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.service.metadata.extractor.PdfMetadataExtractor;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.booklore.model.enums.BookdropFileStatus.PENDING_REVIEW;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookdropMetadataServiceTest {

    @Mock
    private JooqBookdropFileRepository bookdropFileRepository;
    @Mock
    private AppSettingService appSettingService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private EpubMetadataExtractor epubMetadataExtractor;
    @Mock
    private PdfMetadataExtractor pdfMetadataExtractor;
    @Mock
    private CbxMetadataExtractor cbxMetadataExtractor;
    @Mock
    private MetadataRefreshService metadataRefreshService;
    @Mock
    private FileService fileService;
    @Mock
    private MetadataExtractorFactory metadataExtractorFactory;

    @InjectMocks
    private BookdropMetadataService bookdropMetadataService;

    private BookdropFileRow sampleFile;

    @BeforeEach
    void setup() {
        sampleFile = row(1L, "/tmp/book.epub", "book.epub", null);
    }

    private static BookdropFileRow row(Long id, String filePath, String fileName, String originalMetadata) {
        return new BookdropFileRow(id, filePath, fileName, null, PENDING_REVIEW, originalMetadata, null, null, null);
    }

    @Test
    void attachInitialMetadata_shouldExtractAndSaveMetadata() throws Exception {
        BookMetadata metadata = BookMetadata.builder().title("Test Book").build();

        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(metadataExtractorFactory.extractMetadata(eq(BookFileExtension.EPUB), any(File.class))).thenReturn(metadata);
        when(objectMapper.writeValueAsString(any(BookMetadata.class))).thenReturn("{\"title\":\"Test Book\"}");
        when(bookdropFileRepository.updateOriginalMetadata(anyLong(), anyString())).thenAnswer(invocation ->
                new BookdropFileRow(1L, "/tmp/book.epub", "book.epub", null, PENDING_REVIEW,
                        invocation.getArgument(1), null, null, Instant.now()));

        BookdropFileRow result = bookdropMetadataService.attachInitialMetadata(1L);

        assertThat(result).isNotNull();
        assertThat(result.getOriginalMetadata()).contains("Test Book");
        assertThat(result.getUpdatedAt()).isBeforeOrEqualTo(Instant.now());
        verify(bookdropFileRepository).updateOriginalMetadata(anyLong(), anyString());
    }

    @Test
    void attachInitialMetadata_shouldThrowWhenFileMissing() {
        when(bookdropFileRepository.findById(99L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> bookdropMetadataService.attachInitialMetadata(99L));
    }

    @Test
    void attachFetchedMetadata_shouldUpdateEntityWithFetchedData() throws Exception {
        BookdropFileRow file = row(1L, "/tmp/book.epub", "book.epub", "{\"title\":\"Old Book\"}");
        AppSettings settings = new AppSettings();
        BookMetadata fetched = BookMetadata.builder().title("New Title").build();

        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(metadataRefreshService.prepareProviders(any())).thenReturn(List.of());
        when(objectMapper.readValue(file.getOriginalMetadata(), BookMetadata.class)).thenReturn(fetched);
        when(metadataRefreshService.fetchMetadataForBook(any(), any(Book.class))).thenReturn(Map.of());
        when(metadataRefreshService.buildFetchMetadata(any(), any(), any(), any())).thenReturn(fetched);
        when(objectMapper.writeValueAsString(fetched)).thenReturn("{\"title\":\"New Title\"}");
        when(bookdropFileRepository.updateFetchedMetadataAndStatus(anyLong(), anyString(), any())).thenAnswer(invocation ->
                new BookdropFileRow(1L, "/tmp/book.epub", "book.epub", null, invocation.getArgument(2),
                        "{\"title\":\"Old Book\"}", invocation.getArgument(1), null, Instant.now()));

        BookdropFileRow result = bookdropMetadataService.attachFetchedMetadata(1L);

        assertThat(result.getFetchedMetadata()).contains("New Title");
        assertThat(result.getStatus()).isEqualTo(PENDING_REVIEW);
        verify(bookdropFileRepository).updateFetchedMetadataAndStatus(anyLong(), anyString(), any());
    }

    @Test
    void attachFetchedMetadata_shouldSkipOnlineFetchWhenNotSearchable() throws Exception {
        // filename base "book" equals the title and there is no ISBN/ASIN, so hasSearchableMetadata is false:
        // the service must skip the online fetch and only flip status to PENDING_REVIEW.
        BookdropFileRow file = row(1L, "/tmp/book.epub", "book.epub", "{\"title\":\"book\"}");
        BookMetadata initial = BookMetadata.builder().title("book").build();

        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(appSettingService.getAppSettings()).thenReturn(new AppSettings());
        when(objectMapper.readValue(file.getOriginalMetadata(), BookMetadata.class)).thenReturn(initial);
        when(bookdropFileRepository.updateStatus(anyLong(), any())).thenAnswer(invocation ->
                new BookdropFileRow(1L, "/tmp/book.epub", "book.epub", null, invocation.getArgument(1),
                        "{\"title\":\"book\"}", null, null, Instant.now()));

        BookdropFileRow result = bookdropMetadataService.attachFetchedMetadata(1L);

        assertThat(result.getStatus()).isEqualTo(PENDING_REVIEW);
        assertThat(result.getFetchedMetadata()).isNull();
        verify(bookdropFileRepository).updateStatus(1L, PENDING_REVIEW);
        verify(bookdropFileRepository, never()).updateFetchedMetadataAndStatus(anyLong(), anyString(), any());
        verify(metadataRefreshService, never()).fetchMetadataForBook(any(), any(Book.class));
    }

    @Test
    void attachInitialMetadata_shouldHandleNullCoverGracefully() throws Exception {
        BookMetadata metadata = BookMetadata.builder().title("No Cover Book").build();

        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(metadataExtractorFactory.extractMetadata(eq(BookFileExtension.EPUB), any(File.class))).thenReturn(metadata);
        when(objectMapper.writeValueAsString(metadata)).thenReturn("{\"title\":\"No Cover Book\"}");
        when(bookdropFileRepository.updateOriginalMetadata(anyLong(), anyString())).thenAnswer(invocation ->
                new BookdropFileRow(1L, "/tmp/book.epub", "book.epub", null, PENDING_REVIEW,
                        invocation.getArgument(1), null, null, Instant.now()));

        BookdropFileRow result = bookdropMetadataService.attachInitialMetadata(1L);

        assertThat(result.getOriginalMetadata()).contains("No Cover Book");
        verify(bookdropFileRepository).updateOriginalMetadata(anyLong(), anyString());
    }

    @Test
    void extractInitialMetadata_shouldThrowForUnsupportedFileExtension() {
        BookdropFileRow file = row(1L, "/tmp/book.txt", "book.txt", null);

        when(bookdropFileRepository.findById(file.getId())).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> bookdropMetadataService.attachInitialMetadata(file.getId())).isInstanceOf(APIException.class)
                .hasMessageContaining("Invalid file format");
    }

    @Test
    void attachFetchedMetadata_shouldSleepIfGoodreadsIncluded() throws Exception {
        BookdropFileRow file = row(1L, "/tmp/book.epub", "book.epub", "{\"title\":\"Book\"}");
        AppSettings settings = new AppSettings();
        BookMetadata fetched = BookMetadata.builder().title("Fetched Book").build();

        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(metadataRefreshService.prepareProviders(any())).thenReturn(List.of(MetadataProvider.GoodReads));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(fetched);
        when(metadataRefreshService.fetchMetadataForBook(any(), any(Book.class))).thenReturn(Map.of());
        when(metadataRefreshService.buildFetchMetadata(any(), any(), any(), any())).thenReturn(fetched);
        when(objectMapper.writeValueAsString(fetched)).thenReturn("{\"title\":\"Fetched Book\"}");
        when(bookdropFileRepository.updateFetchedMetadataAndStatus(anyLong(), anyString(), any())).thenAnswer(invocation ->
                new BookdropFileRow(1L, "/tmp/book.epub", "book.epub", null, invocation.getArgument(2),
                        "{\"title\":\"Book\"}", invocation.getArgument(1), null, Instant.now()));

        BookdropFileRow result = bookdropMetadataService.attachFetchedMetadata(1L);

        assertThat(result.getFetchedMetadata()).contains("Fetched Book");
        assertThat(result.getStatus()).isEqualTo(PENDING_REVIEW);
        verify(bookdropFileRepository).updateFetchedMetadataAndStatus(anyLong(), anyString(), any());
    }

    @Test
    void attachFetchedMetadata_shouldThrowOnJsonProcessingError() throws Exception {
        BookdropFileRow file = row(1L, "/tmp/book.epub", "book.epub", "{invalidJson}");

        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(appSettingService.getAppSettings()).thenReturn(new AppSettings());
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class)))
                .thenThrow(new JacksonException("Invalid JSON") {
                });

        assertThatThrownBy(() -> bookdropMetadataService.attachFetchedMetadata(1L))
                .isInstanceOf(JacksonException.class);
    }
}
