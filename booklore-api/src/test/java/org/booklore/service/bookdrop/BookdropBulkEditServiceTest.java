package org.booklore.service.bookdrop;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.BookdropBulkEditRequest;
import org.booklore.model.dto.response.BookdropBulkEditResult;
import org.booklore.model.enums.BookdropFileStatus;
import org.booklore.repository.jooq.JooqBookdropFileRepository;
import org.booklore.repository.jooq.dto.BookdropFileRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookdropBulkEditServiceTest {

    @Mock
    private JooqBookdropFileRepository bookdropFileRepository;

    @Mock
    private BookdropMetadataHelper metadataHelper;

    @InjectMocks
    private BookdropBulkEditService bulkEditService;

    @Captor
    private ArgumentCaptor<Map<Long, String>> mapCaptor;

    private BookdropFileRow createFileEntity(Long id, String fileName, BookMetadata metadata) {
        return new BookdropFileRow(id, "/bookdrop/" + fileName, fileName, null,
                BookdropFileStatus.PENDING_REVIEW, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        when(metadataHelper.getCurrentMetadata(any())).thenReturn(new BookMetadata());
        when(metadataHelper.serializeMetadata(any(), any())).thenReturn("{}");
    }

    @Test
    void bulkEdit_WithSingleValueFields_ShouldUpdateTextAndNumericFields() {
        BookMetadata existingMetadata = new BookMetadata();
        existingMetadata.setSeriesName("Old Series");

        BookdropFileRow file1 = createFileEntity(1L, "file1.cbz", existingMetadata);
        BookdropFileRow file2 = createFileEntity(2L, "file2.cbz", existingMetadata);

        when(metadataHelper.resolveFileIds(false, null, List.of(1L, 2L)))
                .thenReturn(List.of(1L, 2L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file1, file2));

        BookMetadata updates = new BookMetadata();
        updates.setSeriesName("New Series");
        updates.setPublisher("Test Publisher");
        updates.setLanguage("en");
        updates.setSeriesTotal(100);

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("seriesName", "publisher", "language", "seriesTotal"));
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L, 2L));

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(2, result.getTotalFiles());
        assertEquals(2, result.getSuccessfullyUpdated());
        assertEquals(0, result.getFailed());

        verify(metadataHelper, times(2)).serializeMetadata(any(), any());
        verify(bookdropFileRepository, times(1)).updateFetchedMetadataForIds(anyMap());
    }

    @Test
    void bulkEdit_WithArrayFieldsMergeMode_ShouldMergeArrays() {
        BookMetadata existingMetadata = new BookMetadata();
        existingMetadata.setAuthors(new ArrayList<>(List.of("Author 1")));
        existingMetadata.setCategories(new LinkedHashSet<>(List.of("Category 1")));

        when(metadataHelper.getCurrentMetadata(any())).thenReturn(existingMetadata);

        BookdropFileRow file = createFileEntity(1L, "file.cbz", existingMetadata);

        when(metadataHelper.resolveFileIds(false, null, List.of(1L)))
                .thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file));

        BookMetadata updates = new BookMetadata();
        updates.setAuthors(new ArrayList<>(List.of("Author 2")));
        updates.setCategories(new LinkedHashSet<>(List.of("Category 2")));

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("authors", "categories"));
        request.setMergeArrays(true);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L));

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getSuccessfullyUpdated());
        assertEquals(0, result.getFailed());

        ArgumentCaptor<BookMetadata> metadataCaptor = ArgumentCaptor.forClass(BookMetadata.class);
        verify(metadataHelper).serializeMetadata(any(), metadataCaptor.capture());

        BookMetadata captured = metadataCaptor.getValue();
        assertTrue(captured.getAuthors().contains("Author 1"));
        assertTrue(captured.getAuthors().contains("Author 2"));
        assertTrue(captured.getCategories().contains("Category 1"));
        assertTrue(captured.getCategories().contains("Category 2"));
    }

    @Test
    void bulkEdit_WithArrayFieldsReplaceMode_ShouldReplaceArrays() {
        BookMetadata existingMetadata = new BookMetadata();
        existingMetadata.setAuthors(new ArrayList<>(List.of("Author 1")));

        when(metadataHelper.getCurrentMetadata(any())).thenReturn(existingMetadata);

        BookdropFileRow file = createFileEntity(1L, "file.cbz", existingMetadata);

        when(metadataHelper.resolveFileIds(false, null, List.of(1L)))
                .thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file));

        BookMetadata updates = new BookMetadata();
        updates.setAuthors(new ArrayList<>(List.of("Author 2")));

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("authors"));
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L));

        bulkEditService.bulkEdit(request);

        ArgumentCaptor<BookMetadata> metadataCaptor = ArgumentCaptor.forClass(BookMetadata.class);
        verify(metadataHelper).serializeMetadata(any(), metadataCaptor.capture());

        BookMetadata captured = metadataCaptor.getValue();
        assertFalse(captured.getAuthors().contains("Author 1"));
        assertTrue(captured.getAuthors().contains("Author 2"));
        assertEquals(1, captured.getAuthors().size());
    }

    @Test
    void bulkEdit_WithDisabledFields_ShouldNotUpdateThoseFields() {
        BookMetadata existingMetadata = new BookMetadata();
        existingMetadata.setSeriesName("Original Series");
        existingMetadata.setPublisher("Original Publisher");

        when(metadataHelper.getCurrentMetadata(any())).thenReturn(existingMetadata);

        BookdropFileRow file = createFileEntity(1L, "file.cbz", existingMetadata);

        when(metadataHelper.resolveFileIds(false, null, List.of(1L)))
                .thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file));

        BookMetadata updates = new BookMetadata();
        updates.setSeriesName("New Series");
        updates.setPublisher("New Publisher");

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("seriesName"));
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L));

        bulkEditService.bulkEdit(request);

        ArgumentCaptor<BookMetadata> metadataCaptor = ArgumentCaptor.forClass(BookMetadata.class);
        verify(metadataHelper).serializeMetadata(any(), metadataCaptor.capture());

        BookMetadata captured = metadataCaptor.getValue();
        assertEquals("New Series", captured.getSeriesName());
        assertEquals("Original Publisher", captured.getPublisher());
    }

    @Test
    void bulkEdit_WithSelectAll_ShouldProcessAllFiles() {
        BookdropFileRow file1 = createFileEntity(1L, "file1.cbz", new BookMetadata());
        BookdropFileRow file2 = createFileEntity(2L, "file2.cbz", new BookMetadata());
        BookdropFileRow file3 = createFileEntity(3L, "file3.cbz", new BookMetadata());

        when(metadataHelper.resolveFileIds(true, List.of(2L), null))
                .thenReturn(List.of(1L, 3L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file1, file3));

        BookMetadata updates = new BookMetadata();
        updates.setLanguage("en");

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("language"));
        request.setMergeArrays(false);
        request.setSelectAll(true);
        request.setExcludedIds(List.of(2L));

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(2, result.getTotalFiles());
        assertEquals(2, result.getSuccessfullyUpdated());
        verify(metadataHelper, times(2)).serializeMetadata(any(), any());
    }

    @Test
    void bulkEdit_WithOneFileError_ShouldContinueWithOthers() {
        BookdropFileRow file1 = createFileEntity(1L, "file1.cbz", new BookMetadata());
        BookdropFileRow file2 = createFileEntity(2L, "file2.cbz", new BookMetadata());
        BookdropFileRow file3 = createFileEntity(3L, "file3.cbz", new BookMetadata());

        when(metadataHelper.resolveFileIds(false, null, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file1, file2, file3));

        doThrow(new RuntimeException("JSON serialization error"))
                .when(metadataHelper).serializeMetadata(eq(file2), any());

        BookMetadata updates = new BookMetadata();
        updates.setLanguage("en");

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("language"));
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L, 2L, 3L));

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(3, result.getTotalFiles());
        assertEquals(2, result.getSuccessfullyUpdated());
        assertEquals(1, result.getFailed());

        verify(bookdropFileRepository).updateFetchedMetadataForIds(mapCaptor.capture());
        Map<Long, String> savedFiles = mapCaptor.getValue();
        assertEquals(2, savedFiles.size());
        assertTrue(savedFiles.containsKey(1L));
        assertTrue(savedFiles.containsKey(3L));
        assertFalse(savedFiles.containsKey(2L));
    }

    @Test
    void bulkEdit_WithEmptyEnabledFields_ShouldNotUpdateAnything() {
        BookdropFileRow file = createFileEntity(1L, "file.cbz", new BookMetadata());

        when(metadataHelper.resolveFileIds(false, null, List.of(1L)))
                .thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file));

        BookMetadata updates = new BookMetadata();
        updates.setSeriesName("New Series");

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Collections.emptySet());
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L));

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(1, result.getSuccessfullyUpdated());

        ArgumentCaptor<BookMetadata> metadataCaptor = ArgumentCaptor.forClass(BookMetadata.class);
        verify(metadataHelper).serializeMetadata(any(), metadataCaptor.capture());

        assertNull(metadataCaptor.getValue().getSeriesName());
    }

    @Test
    void bulkEdit_WithLargeSelection_ShouldProcessInBatches() {
        List<BookdropFileRow> batch1 = new ArrayList<>();
        List<BookdropFileRow> batch2 = new ArrayList<>();
        List<BookdropFileRow> batch3 = new ArrayList<>();
        List<Long> manyIds = new ArrayList<>();

        for (long i = 1; i <= 1500; i++) {
            manyIds.add(i);
            BookdropFileRow file = createFileEntity(i, "file" + i + ".cbz", new BookMetadata());
            if (i <= 500) {
                batch1.add(file);
            } else if (i <= 1000) {
                batch2.add(file);
            } else {
                batch3.add(file);
            }
        }

        when(metadataHelper.resolveFileIds(false, null, manyIds))
                .thenReturn(manyIds);

        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(batch1, batch2, batch3);

        BookMetadata updates = new BookMetadata();
        updates.setLanguage("en");

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("language"));
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(manyIds);

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(1500, result.getTotalFiles());
        assertEquals(1500, result.getSuccessfullyUpdated());
        assertEquals(0, result.getFailed());

        verify(bookdropFileRepository, times(3)).findAllById(anyList());
        verify(bookdropFileRepository, times(3)).updateFetchedMetadataForIds(anyMap());
    }
}
