package org.booklore.service;

import org.booklore.mapper.AdditionalFileMapper;
import org.booklore.model.dto.BookFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.service.file.AdditionalFileService;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdditionalFileServiceTest {

    @Mock
    private JooqBookEntityRepository jooqBookEntityRepository;

    @Mock
    private JooqBookRepository jooqBookRepository;

    @Mock
    private AdditionalFileMapper additionalFileMapper;

    @Mock
    private MonitoringRegistrationService monitoringRegistrationService;

    @InjectMocks
    private AdditionalFileService additionalFileService;

    @TempDir
    Path tempDir;

    private BookFileEntity fileEntity;
    private BookFile additionalFile;
    private BookEntity bookEntity;

    @BeforeEach
    void setUp() throws IOException {
        Path testFile = tempDir.resolve("test-file.pdf");
        Files.createFile(testFile);

        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath(tempDir.toString());

        bookEntity = new BookEntity();
        bookEntity.setId(100L);
        bookEntity.setLibraryPath(libraryPathEntity);

        fileEntity = new BookFileEntity();
        fileEntity.setId(1L);
        fileEntity.setBook(bookEntity);
        fileEntity.setFileName("test-file.pdf");
        fileEntity.setFileSubPath(".");
        fileEntity.setBookFormat(true);

        additionalFile = mock(BookFile.class);
    }

    @Test
    void getAdditionalFilesByBookId_WhenFilesExist_ShouldReturnMappedFiles() {
        Long bookId = 100L;
        List<BookFileEntity> entities = List.of(fileEntity);
        List<BookFile> expectedFiles = List.of(additionalFile);

        when(jooqBookEntityRepository.findBookFilesByBookId(bookId)).thenReturn(entities);
        when(additionalFileMapper.toAdditionalFiles(entities)).thenReturn(expectedFiles);

        List<BookFile> result = additionalFileService.getAdditionalFilesByBookId(bookId);

        assertEquals(expectedFiles, result);
        verify(jooqBookEntityRepository).findBookFilesByBookId(bookId);
        verify(additionalFileMapper).toAdditionalFiles(entities);
    }

    @Test
    void getAdditionalFilesByBookId_WhenNoFilesExist_ShouldReturnEmptyList() {
        Long bookId = 100L;
        List<BookFileEntity> entities = Collections.emptyList();
        List<BookFile> expectedFiles = Collections.emptyList();

        when(jooqBookEntityRepository.findBookFilesByBookId(bookId)).thenReturn(entities);
        when(additionalFileMapper.toAdditionalFiles(entities)).thenReturn(expectedFiles);

        List<BookFile> result = additionalFileService.getAdditionalFilesByBookId(bookId);

        assertTrue(result.isEmpty());
        verify(jooqBookEntityRepository).findBookFilesByBookId(bookId);
        verify(additionalFileMapper).toAdditionalFiles(entities);
    }

    @Test
    void getAdditionalFilesByBookIdAndType_WhenFilesExist_ShouldReturnMappedFiles() {
        Long bookId = 100L;
        boolean isBook = true;
        List<BookFileEntity> entities = List.of(fileEntity);
        List<BookFile> expectedFiles = List.of(additionalFile);

        when(jooqBookEntityRepository.findBookFilesByBookIdAndIsBookFormat(bookId, isBook)).thenReturn(entities);
        when(additionalFileMapper.toAdditionalFiles(entities)).thenReturn(expectedFiles);

        List<BookFile> result = additionalFileService.getAdditionalFilesByBookIdAndIsBook(bookId, isBook);

        assertEquals(expectedFiles, result);
        verify(jooqBookEntityRepository).findBookFilesByBookIdAndIsBookFormat(bookId, isBook);
        verify(additionalFileMapper).toAdditionalFiles(entities);
    }

    @Test
    void getAdditionalFilesByBookIdAndType_WhenNoFilesExist_ShouldReturnEmptyList() {
        Long bookId = 100L;
        boolean isBook = false;
        List<BookFileEntity> entities = Collections.emptyList();
        List<BookFile> expectedFiles = Collections.emptyList();

        when(jooqBookEntityRepository.findBookFilesByBookIdAndIsBookFormat(bookId, isBook)).thenReturn(entities);
        when(additionalFileMapper.toAdditionalFiles(entities)).thenReturn(expectedFiles);

        List<BookFile> result = additionalFileService.getAdditionalFilesByBookIdAndIsBook(bookId, isBook);

        assertTrue(result.isEmpty());
        verify(jooqBookEntityRepository).findBookFilesByBookIdAndIsBookFormat(bookId, isBook);
        verify(additionalFileMapper).toAdditionalFiles(entities);
    }

    @Test
    void deleteAdditionalFile_WhenFileNotFound_ShouldThrowException() {
        Long fileId = 1L;
        when(jooqBookEntityRepository.findBookFileById(fileId)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> additionalFileService.deleteAdditionalFile(fileId)
        );

        assertEquals("Additional file not found with id: 1", exception.getMessage());
        verify(jooqBookEntityRepository).findBookFileById(fileId);
        verify(jooqBookRepository, never()).deleteBookFileById(anyLong());
        verify(monitoringRegistrationService, never()).unregisterSpecificPath(any());
    }

    @Test
    void deleteAdditionalFile_WhenFileExists_ShouldDeleteSuccessfully() {
        Long fileId = 1L;
        Path parentPath = fileEntity.getFullFilePath().getParent();

        when(jooqBookEntityRepository.findBookFileById(fileId)).thenReturn(fileEntity);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.deleteIfExists(fileEntity.getFullFilePath())).thenReturn(true);

            additionalFileService.deleteAdditionalFile(fileId);

            verify(jooqBookEntityRepository).findBookFileById(fileId);
            verify(monitoringRegistrationService).unregisterSpecificPath(parentPath);
            filesMock.verify(() -> Files.deleteIfExists(fileEntity.getFullFilePath()));
            verify(jooqBookRepository).deleteBookFileById(fileId);
        }
    }

    @Test
    void deleteAdditionalFile_WhenIOExceptionOccurs_ShouldStillDeleteFromRepository() {
        Long fileId = 1L;
        Path parentPath = fileEntity.getFullFilePath().getParent();

        when(jooqBookEntityRepository.findBookFileById(fileId)).thenReturn(fileEntity);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.deleteIfExists(fileEntity.getFullFilePath())).thenThrow(new IOException("File access error"));

            additionalFileService.deleteAdditionalFile(fileId);

            verify(jooqBookEntityRepository).findBookFileById(fileId);
            verify(monitoringRegistrationService).unregisterSpecificPath(parentPath);
            filesMock.verify(() -> Files.deleteIfExists(fileEntity.getFullFilePath()));
            verify(jooqBookRepository).deleteBookFileById(fileId);
        }
    }

    @Test
    void deleteAdditionalFile_WhenEntityRelationshipsMissing_ShouldThrowIllegalStateException() {
        Long fileId = 1L;
        BookFileEntity invalidEntity = new BookFileEntity();
        invalidEntity.setId(fileId);

        when(jooqBookEntityRepository.findBookFileById(fileId)).thenReturn(invalidEntity);

        assertThrows(
                IllegalStateException.class,
                () -> additionalFileService.deleteAdditionalFile(fileId)
        );

        verify(jooqBookEntityRepository).findBookFileById(fileId);
        verify(jooqBookRepository, never()).deleteBookFileById(anyLong());
        verify(monitoringRegistrationService, never()).unregisterSpecificPath(any());
    }

    @Test
    void downloadAdditionalFile_WhenFileNotFound_ShouldReturnNotFound() throws IOException {
        Long fileId = 1L;
        when(jooqBookEntityRepository.findBookFileById(fileId)).thenReturn(null);

        ResponseEntity<Resource> result = additionalFileService.downloadAdditionalFile(fileId);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertNull(result.getBody());
        verify(jooqBookEntityRepository).findBookFileById(fileId);
    }

    @Test
    void downloadAdditionalFile_WhenPhysicalFileNotExists_ShouldReturnNotFound() throws IOException {
        Long fileId = 1L;

        BookFileEntity entityWithNonExistentFile = new BookFileEntity();
        entityWithNonExistentFile.setId(fileId);
        entityWithNonExistentFile.setBook(bookEntity);
        entityWithNonExistentFile.setFileName("non-existent.pdf");
        entityWithNonExistentFile.setFileSubPath(".");

        when(jooqBookEntityRepository.findBookFileById(fileId)).thenReturn(entityWithNonExistentFile);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            Path actualPath = entityWithNonExistentFile.getFullFilePath();
            filesMock.when(() -> Files.exists(actualPath)).thenReturn(false);

            ResponseEntity<Resource> result = additionalFileService.downloadAdditionalFile(fileId);

            assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
            assertNull(result.getBody());
            verify(jooqBookEntityRepository).findBookFileById(fileId);
            filesMock.verify(() -> Files.exists(actualPath));
        }
    }

    @Test
    void downloadAdditionalFile_WhenFileExists_ShouldReturnFileResource() throws Exception {
        Long fileId = 1L;
        when(jooqBookEntityRepository.findBookFileById(fileId)).thenReturn(fileEntity);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(fileEntity.getFullFilePath())).thenReturn(true);

            ResponseEntity<Resource> result = additionalFileService.downloadAdditionalFile(fileId);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertNotNull(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
            assertTrue(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("test-file.pdf"));
            assertEquals(MediaType.APPLICATION_OCTET_STREAM, result.getHeaders().getContentType());

            verify(jooqBookEntityRepository).findBookFileById(fileId);
            filesMock.verify(() -> Files.exists(fileEntity.getFullFilePath()));
        }
    }

    @Test
    void downloadAdditionalFile_WhenEntityRelationshipsMissing_ShouldThrowIllegalStateException() {
        Long fileId = 1L;
        BookFileEntity invalidEntity = new BookFileEntity();
        invalidEntity.setId(fileId);

        when(jooqBookEntityRepository.findBookFileById(fileId)).thenReturn(invalidEntity);

        assertThrows(
                IllegalStateException.class,
                () -> additionalFileService.downloadAdditionalFile(fileId)
        );

        verify(jooqBookEntityRepository).findBookFileById(fileId);
    }
}
