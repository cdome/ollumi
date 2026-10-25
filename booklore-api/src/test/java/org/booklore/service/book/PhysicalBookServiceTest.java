package org.booklore.service.book;

import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.request.CreatePhysicalBookRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqLibraryRepository;
import org.booklore.repository.jooq.JooqBookMetadataRelationsRepository;
import org.booklore.repository.jooq.JooqBookMetadataRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.util.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhysicalBookServiceTest {

    @Mock private JooqBookEntityRepository jooqBookEntityRepository;
    @Mock private JooqLibraryRepository libraryRepository;
    @Mock private JooqBookMetadataRelationsRepository relationsRepository;
    @Mock private JooqBookRepository jooqBookRepository;
    @Mock private JooqBookMetadataRepository jooqBookMetadataRepository;
    @Mock private BookMapper bookMapper;
    @Mock private FileService fileService;

    @InjectMocks
    private PhysicalBookService physicalBookService;

    @Test
    void createPhysicalBook_libraryNotFound_throws() {
        CreatePhysicalBookRequest request = CreatePhysicalBookRequest.builder().libraryId(99L).title("Ghost").build();
        when(libraryRepository.findById(99L)).thenReturn(null);

        assertThrows(APIException.class, () -> physicalBookService.createPhysicalBook(request));

        verifyNoInteractions(jooqBookRepository, jooqBookMetadataRepository);
    }

    @Test
    void createPhysicalBook_minimal_insertsBareBookAndMetadata() {
        LibraryEntity library = LibraryEntity.builder().id(1L).name("Physical Library").build();
        when(libraryRepository.findById(1L)).thenReturn(library);
        when(jooqBookRepository.insertBook(eq(1L), isNull(), any())).thenReturn(42L);
        when(bookMapper.toBook(any(BookEntity.class))).thenReturn(Book.builder().build());

        CreatePhysicalBookRequest request = CreatePhysicalBookRequest.builder()
                .libraryId(1L)
                .title("Moby Dick")
                .isbn("9780142437247")
                .build();

        Book result = physicalBookService.createPhysicalBook(request);

        verify(jooqBookRepository).insertBook(eq(1L), isNull(), any());

        ArgumentCaptor<BookMetadataEntity> metadataCaptor = ArgumentCaptor.forClass(BookMetadataEntity.class);
        verify(jooqBookMetadataRepository).insertRow(eq(42L), metadataCaptor.capture());
        BookMetadataEntity insertedMetadata = metadataCaptor.getValue();
        assertThat(insertedMetadata.getBookId()).isEqualTo(42L);
        assertThat(insertedMetadata.getTitle()).isEqualTo("Moby Dick");
        assertThat(insertedMetadata.getIsbn13()).isEqualTo("9780142437247");

        verify(jooqBookMetadataRepository).updateFullRow(any(BookMetadataEntity.class));
        verifyNoInteractions(relationsRepository);
        verifyNoInteractions(fileService);
        assertThat(result).isNotNull();
    }

    @Test
    void createPhysicalBook_withAuthorsAndCategories_writesRelationsThenFinalFullRow() {
        LibraryEntity library = LibraryEntity.builder().id(1L).name("Physical Library").build();
        when(libraryRepository.findById(1L)).thenReturn(library);
        when(jooqBookRepository.insertBook(eq(1L), isNull(), any())).thenReturn(7L);
        when(bookMapper.toBook(any(BookEntity.class))).thenReturn(Book.builder().build());

        CreatePhysicalBookRequest request = CreatePhysicalBookRequest.builder()
                .libraryId(1L)
                .title("Dune")
                .authors(List.of("Frank Herbert"))
                .categories(List.of("Science Fiction"))
                .build();

        physicalBookService.createPhysicalBook(request);

        verify(relationsRepository).setAuthorsForBook(eq(7L), eq(List.of("Frank Herbert")));
        verify(relationsRepository).setCategoriesForBook(eq(7L), eq(Set.of("Science Fiction")));
        verify(jooqBookMetadataRepository).updateFullRow(any(BookMetadataEntity.class));
    }

    @Test
    void togglePhysicalFlag_updatesJooqAndReturnsMappedBook() {
        BookEntity book = new BookEntity();
        book.setId(5L);
        when(jooqBookEntityRepository.findByIdFull(5L)).thenReturn(book);
        when(bookMapper.toBook(book)).thenReturn(Book.builder().build());

        Book result = physicalBookService.togglePhysicalFlag(5L, true);

        assertThat(book.getIsPhysical()).isTrue();
        verify(jooqBookRepository).updateIsPhysical(5L, true);
        assertThat(result).isNotNull();
    }

    @Test
    void togglePhysicalFlag_bookNotFound_throws() {
        when(jooqBookEntityRepository.findByIdFull(404L)).thenReturn(null);

        assertThrows(APIException.class, () -> physicalBookService.togglePhysicalFlag(404L, false));

        verifyNoInteractions(jooqBookRepository);
    }
}
