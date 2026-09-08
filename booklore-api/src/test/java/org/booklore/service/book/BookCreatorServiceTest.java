package org.booklore.service.book;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookCreatorServiceTest {

    @Mock private org.booklore.repository.jooq.JooqBookMetadataRelationsRepository relationsRepository;
    @Mock private BookRepository bookRepository;
    @Mock private org.booklore.repository.jooq.JooqBookRepository jooqBookRepository;
    @Mock private org.booklore.repository.jooq.JooqBookMetadataRepository jooqBookMetadataRepository;
    @Mock private org.booklore.repository.jooq.JooqComicMetadataRepository comicRelationsRepository;

    @InjectMocks
    private BookCreatorService bookCreatorService;

    private BookEntity bookEntity;

    @BeforeEach
    void setUp() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        bookEntity = BookEntity.builder().metadata(metadata).build();
    }

    @Test
    void addAuthorsToBook_nullAuthors_doesNothing() {
        bookCreatorService.addAuthorsToBook(null, bookEntity);

        assertNull(bookEntity.getMetadata().getAuthorNames());
        verifyNoInteractions(relationsRepository);
    }

    @Test
    void addAuthorsToBook_emptyAuthors_doesNothing() {
        bookCreatorService.addAuthorsToBook(Set.of(), bookEntity);

        assertNull(bookEntity.getMetadata().getAuthorNames());
        verifyNoInteractions(relationsRepository);
    }

    @Test
    void addAuthorsToBook_validAuthors_addsToBook() {
        bookEntity.setId(1L);

        bookCreatorService.addAuthorsToBook(Set.of("Test Author"), bookEntity);

        verify(relationsRepository).setAuthorsForBook(1L, List.of("Test Author"));
        assertEquals(List.of("Test Author"), bookEntity.getMetadata().getAuthorNames());
    }

    @Test
    void addCategoriesToBook_nullCategories_doesNothing() {
        bookCreatorService.addCategoriesToBook(null, bookEntity);

        verifyNoInteractions(relationsRepository);
    }

    @Test
    void addCategoriesToBook_emptyCategories_doesNothing() {
        bookCreatorService.addCategoriesToBook(Set.of(), bookEntity);

        verifyNoInteractions(relationsRepository);
    }

    @Test
    void addCategoriesToBook_validCategories_addsToBook() {
        bookEntity.setId(1L);

        bookCreatorService.addCategoriesToBook(Set.of("Fiction"), bookEntity);

        verify(relationsRepository).setCategoriesForBook(1L, Set.of("Fiction"));
    }

    @Test
    void addMoodsToBook_nullMoods_doesNothing() {
        bookCreatorService.addMoodsToBook(null, bookEntity);

        verifyNoInteractions(relationsRepository);
    }

    @Test
    void addMoodsToBook_emptyMoods_doesNothing() {
        bookCreatorService.addMoodsToBook(Set.of(), bookEntity);

        verifyNoInteractions(relationsRepository);
    }

    @Test
    void addMoodsToBook_validMoods_addsToBook() {
        bookEntity.setId(1L);

        bookCreatorService.addMoodsToBook(Set.of("Dark"), bookEntity);

        verify(relationsRepository).setMoodsForBook(1L, Set.of("Dark"));
    }

    @Test
    void addTagsToBook_nullTags_doesNothing() {
        bookCreatorService.addTagsToBook(null, bookEntity);

        verifyNoInteractions(relationsRepository);
    }

    @Test
    void addTagsToBook_emptyTags_doesNothing() {
        bookCreatorService.addTagsToBook(Set.of(), bookEntity);

        verifyNoInteractions(relationsRepository);
    }

    @Test
    void addTagsToBook_validTags_addsToBook() {
        bookEntity.setId(1L);

        bookCreatorService.addTagsToBook(Set.of("favorite"), bookEntity);

        verify(relationsRepository).setTagsForBook(1L, Set.of("favorite"));
    }

    @Test
    void addAuthorsToBook_existingAuthorsOnEntity_replacesThem() {
        bookEntity.setId(1L);
        bookEntity.getMetadata().setAuthorNames(new ArrayList<>(List.of("Existing")));

        bookCreatorService.addAuthorsToBook(Set.of("New Author"), bookEntity);

        verify(relationsRepository).setAuthorsForBook(1L, List.of("New Author"));
        assertEquals(List.of("New Author"), bookEntity.getMetadata().getAuthorNames());
    }
}
