package org.booklore.service.library;

import org.booklore.model.FileProcessResult;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookFile;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.FileProcessStatus;
import org.booklore.repository.jooq.JooqLibraryRepository;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.service.event.BookEventBroadcaster;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.fileprocessor.BookFileProcessor;
import org.booklore.service.fileprocessor.BookFileProcessorRegistry;
import org.booklore.service.kobo.KoboAutoShelfService;
import org.booklore.service.metadata.extractor.AudiobookMetadataExtractor;
import org.booklore.service.metadata.extractor.FileMetadataExtractor;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileAsBookProcessorTest {

    @Mock
    private BookEventBroadcaster bookEventBroadcaster;

    @Mock
    private BookFileProcessorRegistry processorRegistry;

    @Mock
    private KoboAutoShelfService koboAutoShelfService;

    @Mock
    private JooqBookEntityRepository jooqBookEntityRepository;

    @Mock
    private JooqBookRepository jooqBookRepository;

    @Mock
    private JooqLibraryRepository libraryRepository;

    @Mock
    private FileService fileService;

    @Mock
    private MetadataExtractorFactory metadataExtractorFactory;

    @Mock
    private AudiobookMetadataExtractor audiobookMetadataExtractor;

    @Mock
    private BookFileProcessor bookFileProcessor;

    private FileAsBookProcessor fileAsBookProcessor;

    @TempDir
    Path tempDir;

    private AutoCloseable mocks;
    private MockedStatic<FileFingerprint> fileFingerprintMock;
    private MockedStatic<FileUtils> fileUtilsMock;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        fileAsBookProcessor = new FileAsBookProcessor(
                bookEventBroadcaster,
                processorRegistry,
                koboAutoShelfService,
                jooqBookEntityRepository,
                jooqBookRepository,
                libraryRepository,
                fileService,
                metadataExtractorFactory,
                audiobookMetadataExtractor
        );
        fileFingerprintMock = mockStatic(FileFingerprint.class);
        fileFingerprintMock.when(() -> FileFingerprint.generateHash(any(Path.class))).thenReturn("testhash");
        fileUtilsMock = mockStatic(FileUtils.class);
        fileUtilsMock.when(() -> FileUtils.getFileSizeInKb(any(Path.class))).thenReturn(100L);

    }

    @AfterEach
    void tearDown() throws Exception {
        if (fileFingerprintMock != null) fileFingerprintMock.close();
        if (fileUtilsMock != null) fileUtilsMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    void staticMocksShouldWork() {
        // Verify static mocks are working
        Path testPath = Path.of("/test/path");
        String hash = FileFingerprint.generateHash(testPath);
        Long size = FileUtils.getFileSizeInKb(testPath);
        assert hash.equals("testhash") : "Expected 'testhash' but got: " + hash;
        assert size.equals(100L) : "Expected 100L but got: " + size;
    }

    @Test
    void processLibraryFiles_shouldProcessDifferentNamedFilesSeparately() {
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/library/path");

        LibraryFile file1 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book1.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book2.pdf")
                .fileSubPath("books")
                .bookFileType(BookFileType.PDF)
                .build();

        Book book1 = Book.builder().id(1L).primaryFile(BookFile.builder().fileName("book1.epub").bookType(BookFileType.EPUB).build()).build();
        Book book2 = Book.builder().id(2L).primaryFile(BookFile.builder().fileName("book2.pdf").bookType(BookFileType.PDF).build()).build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(processorRegistry.getProcessorOrThrow(BookFileType.PDF)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(file1)).thenReturn(new FileProcessResult(book1, FileProcessStatus.NEW));
        when(bookFileProcessor.processFile(file2)).thenReturn(new FileProcessResult(book2, FileProcessStatus.NEW));

        fileAsBookProcessor.processLibraryFiles(List.of(file1, file2), libraryEntity);

        verify(bookEventBroadcaster, times(2)).broadcastBookAddEvent(any());
        verify(bookFileProcessor, times(2)).processFile(any());
    }

    @Test
    void processLibraryFiles_shouldHandleEmptyList() {
        LibraryEntity libraryEntity = new LibraryEntity();

        fileAsBookProcessor.processLibraryFiles(new ArrayList<>(), libraryEntity);

        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
        verify(processorRegistry, never()).getProcessorOrThrow(any());
    }

    @Test
    void processLibraryFiles_shouldSkipFilesWithNullBookFileType() {
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/library/path");

        LibraryFile invalidFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("document.txt")
                .fileSubPath("docs")
                .bookFileType(null)
                .build();

        fileAsBookProcessor.processLibraryFiles(List.of(invalidFile), libraryEntity);

        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
        verify(processorRegistry, never()).getProcessorOrThrow(any());
    }

    @Test
    void processLibraryFiles_shouldNotBroadcastWhenProcessorReturnsNull() {
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/library/path");

        LibraryFile file = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(file)).thenReturn(null);

        fileAsBookProcessor.processLibraryFiles(List.of(file), libraryEntity);

        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
    }

    @Test
    void processLibraryFiles_shouldNotGroupFilesInDifferentDirectories() {
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath("/library/path");

        LibraryFile file1 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.epub")
                .fileSubPath("dir1")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.pdf")
                .fileSubPath("dir2")
                .bookFileType(BookFileType.PDF)
                .build();

        Book book1 = Book.builder().id(1L).primaryFile(BookFile.builder().fileName("book.epub").bookType(BookFileType.EPUB).build()).build();
        Book book2 = Book.builder().id(2L).primaryFile(BookFile.builder().fileName("book.pdf").bookType(BookFileType.PDF).build()).build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(processorRegistry.getProcessorOrThrow(BookFileType.PDF)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(file1)).thenReturn(new FileProcessResult(book1, FileProcessStatus.NEW));
        when(bookFileProcessor.processFile(file2)).thenReturn(new FileProcessResult(book2, FileProcessStatus.NEW));

        fileAsBookProcessor.processLibraryFiles(List.of(file1, file2), libraryEntity);

        // Both should be processed as separate books
        verify(bookFileProcessor, times(2)).processFile(any());
        verify(bookEventBroadcaster, times(2)).broadcastBookAddEvent(any());
    }

    // ========== createAdditionalBookFile ==========

    @Test
    void createAdditionalBookFile_alreadyExists_skipsInsert() {
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        LibraryFile file = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("books")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();
        when(jooqBookEntityRepository.findBookFileByLibraryPathIdAndFileSubPathAndFileName(1L, "books", "book.epub"))
                .thenReturn(new BookFileEntity());

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(10L);

        fileAsBookProcessor.createAdditionalBookFile(bookEntity, file);

        verifyNoInteractions(jooqBookRepository);
    }

    @Test
    void createAdditionalBookFile_newFile_insertsViaJooqWithCorrectArgs() {
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath(tempDir.toString());
        LibraryFile file = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("books")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .folderBased(false)
                .build();
        when(jooqBookEntityRepository.findBookFileByLibraryPathIdAndFileSubPathAndFileName(1L, "books", "book.epub"))
                .thenReturn(null);

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(10L);
        // Primary is also non-audiobook, so the mixed-format cover-generation branch is skipped -
        // this test verifies only the insertBookFile wiring, not cover generation.
        BookFileEntity primary = BookFileEntity.builder().fileName("book.pdf").bookType(BookFileType.PDF).isBookFormat(true).build();
        bookEntity.setBookFiles(new ArrayList<>(List.of(primary)));

        fileAsBookProcessor.createAdditionalBookFile(bookEntity, file);

        verify(jooqBookRepository).insertBookFile(
                eq(10L), eq("book.epub"), eq("books"), eq(true), eq(false), eq(BookFileType.EPUB),
                eq(100L), eq("testhash"), eq("testhash"), isNull(), any());
    }

    // ========== generateCoverFromAdditionalFile ==========

    @Test
    void generateCoverFromAdditionalFile_filelessBookEbookAdditional_writesCoverViaJooq() throws Exception {
        Files.createFile(tempDir.resolve("book.epub"));
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath(tempDir.toString());
        LibraryFile additionalFile = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(20L);
        BookMetadataEntity metadata = new BookMetadataEntity();
        bookEntity.setMetadata(metadata);
        bookEntity.setBookFiles(new ArrayList<>()); // hasFiles() == false

        FileMetadataExtractor extractor = mock(FileMetadataExtractor.class);
        when(metadataExtractorFactory.getExtractor(BookFileType.EPUB)).thenReturn(extractor);
        when(extractor.extractCover(any(File.class))).thenReturn(new byte[]{1, 2, 3});
        when(fileService.saveCoverImages(any(BufferedImage.class), eq(20L))).thenReturn(true);

        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        try (MockedStatic<FileService> fileServiceStatic = mockStatic(FileService.class, CALLS_REAL_METHODS)) {
            fileServiceStatic.when(() -> FileService.readImage(any(InputStream.class))).thenReturn(image);
            fileAsBookProcessor.generateCoverFromAdditionalFile(bookEntity, additionalFile);
        }

        assertThat(bookEntity.getBookCoverHash()).isNotNull();
        assertThat(metadata.getCoverUpdatedOn()).isNotNull();
        verify(jooqBookRepository).updateCoverMetadata(20L, bookEntity.getBookCoverHash(), metadata.getCoverUpdatedOn());
    }

    @Test
    void generateCoverFromAdditionalFile_filelessBookAudiobookAdditional_writesAudiobookCoverViaJooq() throws Exception {
        Files.createFile(tempDir.resolve("book.mp3"));
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath(tempDir.toString());
        LibraryFile additionalFile = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName("book.mp3")
                .bookFileType(BookFileType.AUDIOBOOK)
                .folderBased(false)
                .build();

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(21L);
        BookMetadataEntity metadata = new BookMetadataEntity();
        bookEntity.setMetadata(metadata);
        bookEntity.setBookFiles(new ArrayList<>()); // hasFiles() == false

        when(audiobookMetadataExtractor.extractCover(any(File.class))).thenReturn(new byte[]{1, 2, 3});
        when(fileService.saveAudiobookCoverImages(any(BufferedImage.class), eq(21L))).thenReturn(true);

        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        try (MockedStatic<FileService> fileServiceStatic = mockStatic(FileService.class, CALLS_REAL_METHODS)) {
            fileServiceStatic.when(() -> FileService.readImage(any(InputStream.class))).thenReturn(image);
            fileAsBookProcessor.generateCoverFromAdditionalFile(bookEntity, additionalFile);
        }

        assertThat(bookEntity.getAudiobookCoverHash()).isNotNull();
        assertThat(metadata.getAudiobookCoverUpdatedOn()).isNotNull();
        verify(jooqBookRepository).updateAudiobookCoverMetadata(21L, bookEntity.getAudiobookCoverHash(), metadata.getAudiobookCoverUpdatedOn());
    }
}
