package org.booklore.service.recommender;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookRecommendation;
import org.booklore.model.dto.BookRecommendationLite;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookMetadataRelationsRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.service.book.BookQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookRecommendationServiceTest {

    @Mock private BookSimilarityService similarityService;
    @Mock private JooqBookEntityRepository jooqBookEntityRepository;
    @Mock private JooqBookRepository jooqBookRepository;
    @Mock private BookQueryService bookQueryService;
    @Mock private BookMapper bookMapper;
    @Mock private AuthenticationService authenticationService;
    @Mock private JooqBookMetadataRelationsRepository bookMetadataRelationsRepository;

    @InjectMocks
    private BookRecommendationService service;

    private BookLoreUser adminUser() {
        BookLoreUser.UserPermissions perms = new BookLoreUser.UserPermissions();
        perms.setAdmin(true);
        return BookLoreUser.builder().id(1L).permissions(perms).build();
    }

    @Test
    void getRecommendations_existingRecommendations_doesNotRecomputeOrWrite() {
        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(1L).build();
        BookEntity book = BookEntity.builder().id(1L)
                .metadata(metadata)
                .similarBooksJson(Set.of(new BookRecommendationLite(2L, 0.9)))
                .build();
        when(jooqBookEntityRepository.findByIdWithMetadata(1L)).thenReturn(book);
        when(authenticationService.getAuthenticatedUser()).thenReturn(adminUser());
        when(bookQueryService.findAllWithMetadataByIds(Set.of(2L))).thenReturn(Collections.emptyList());

        List<BookRecommendation> result = service.getRecommendations(1L, 10);

        assertThat(result).isEmpty();
        verifyNoInteractions(jooqBookRepository);
    }

    @Test
    void getRecommendations_noExistingRecommendations_computesAndPersistsViaJooq() {
        BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(1L).build();
        BookEntity book = BookEntity.builder().id(1L).metadata(metadata).similarBooksJson(null).build();
        when(jooqBookEntityRepository.findByIdWithMetadata(1L)).thenReturn(book);
        when(authenticationService.getAuthenticatedUser()).thenReturn(adminUser());
        // No candidates at all - findSimilarBooks returns an empty recommendation list
        when(bookQueryService.getAllFullBookEntities()).thenReturn(Collections.emptyList());
        when(bookQueryService.findAllWithMetadataByIds(Collections.emptySet())).thenReturn(Collections.emptyList());

        List<BookRecommendation> result = service.getRecommendations(1L, 10);

        assertThat(result).isEmpty();
        assertThat(book.getSimilarBooksJson()).isEmpty();
        verify(jooqBookRepository).updateSimilarBooksJson(1L, Collections.emptySet());
    }
}
