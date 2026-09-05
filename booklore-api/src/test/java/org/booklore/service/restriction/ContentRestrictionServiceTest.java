package org.booklore.service.restriction;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ContentRestriction;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.MoodEntity;
import org.booklore.model.entity.TagEntity;
import org.booklore.model.enums.ContentRestrictionMode;
import org.booklore.model.enums.ContentRestrictionType;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqBookMetadataRelationsRepository;
import org.booklore.repository.jooq.JooqUserContentRestrictionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentRestrictionServiceTest {

    @Mock private JooqUserContentRestrictionRepository restrictionRepository;
    @Mock private UserRepository userRepository;
    @Mock private JooqBookMetadataRelationsRepository relationsRepository;
    @InjectMocks private ContentRestrictionService service;

    private static final long USER = 1L;

    /** Parallel entity/DTO fixtures with identical content. */
    private record BookDef(long id, Set<String> categories, Set<String> tags, Set<String> moods,
                           String contentRating, Integer ageRating, boolean hasMetadata) {
    }

    private static final List<BookDef> BOOKS = List.of(
            new BookDef(1, Set.of("Fantasy"), Set.of("Epic"), Set.of("Cozy"), "PG", 10, true),
            new BookDef(2, Set.of("Horror"), Set.of("Dark"), Set.of("Tense"), "R", 18, true),
            new BookDef(3, Set.of("Fantasy", "Horror"), Set.of(), Set.of(), null, null, true),
            new BookDef(4, Set.of(), Set.of(), Set.of(), null, null, true),
            new BookDef(5, Set.of(), Set.of(), Set.of(), null, null, false)
    );

    @Test
    void noRestrictions_returnsAll() {
        when(restrictionRepository.findByUserId(USER)).thenReturn(List.of());
        assertThat(survivingIds(entityBooks())).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(survivingIdsDto(dtoBooks())).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void excludeCategory_dropsMatchingBooks() {
        mockRestrictions(restriction(ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "horror"));
        assertEntityAndDtoAgree(Set.of(1L, 4L, 5L));
    }

    @Test
    void allowOnlyCategory_keepsOnlyMatching() {
        mockRestrictions(restriction(ContentRestrictionType.CATEGORY, ContentRestrictionMode.ALLOW_ONLY, "fantasy"));
        assertEntityAndDtoAgree(Set.of(1L, 3L));
    }

    @Test
    void excludeAgeRating_dropsAtOrAboveMax() {
        mockRestrictions(restriction(ContentRestrictionType.AGE_RATING, ContentRestrictionMode.EXCLUDE, "16"));
        assertEntityAndDtoAgree(Set.of(1L, 3L, 4L, 5L));
    }

    @Test
    void excludeContentRating_dropsMatching() {
        mockRestrictions(restriction(ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.EXCLUDE, "r"));
        assertEntityAndDtoAgree(Set.of(1L, 3L, 4L, 5L));
    }

    @Test
    void combinedRestrictions_entityAndDtoAgree() {
        mockRestrictions(
                restriction(ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "horror"),
                restriction(ContentRestrictionType.TAG, ContentRestrictionMode.ALLOW_ONLY, "epic"),
                restriction(ContentRestrictionType.AGE_RATING, ContentRestrictionMode.EXCLUDE, "16")
        );
        // horror excludes 2,3; allow-only tag=epic keeps only 1; age fine
        assertEntityAndDtoAgree(Set.of(1L));
    }

    // ---- Helpers ----

    private void assertEntityAndDtoAgree(Set<Long> expected) {
        List<Long> fromEntities = survivingIds(entityBooks());
        List<Long> fromDtos = survivingIdsDto(dtoBooks());
        assertThat(fromEntities).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(fromDtos).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(fromDtos).containsExactlyInAnyOrderElementsOf(fromEntities);
    }

    private void mockRestrictions(ContentRestriction... restrictions) {
        when(restrictionRepository.findByUserId(USER)).thenReturn(List.of(restrictions));
        // The entity path now reads category/tag/mood names via the jOOQ relations reader (by book id).
        when(relationsRepository.findCategoryNamesByBookIds(anyList())).thenReturn(namesByBook(BookDef::categories));
        when(relationsRepository.findTagNamesByBookIds(anyList())).thenReturn(namesByBook(BookDef::tags));
        when(relationsRepository.findMoodNamesByBookIds(anyList())).thenReturn(namesByBook(BookDef::moods));
    }

    private Map<Long, Set<String>> namesByBook(Function<BookDef, Set<String>> extractor) {
        return BOOKS.stream().collect(Collectors.toMap(BookDef::id, extractor));
    }

    private List<Long> survivingIds(List<BookEntity> books) {
        return service.applyRestrictions(books, USER).stream().map(BookEntity::getId).collect(Collectors.toList());
    }

    private List<Long> survivingIdsDto(List<Book> books) {
        return service.applyRestrictionsToDtos(books, USER).stream().map(Book::getId).collect(Collectors.toList());
    }

    private List<BookEntity> entityBooks() {
        return BOOKS.stream().map(this::toEntity).collect(Collectors.toList());
    }

    private List<Book> dtoBooks() {
        return BOOKS.stream().map(this::toDto).collect(Collectors.toList());
    }

    private BookEntity toEntity(BookDef d) {
        BookEntity book = new BookEntity();
        book.setId(d.id());
        if (d.hasMetadata()) {
            BookMetadataEntity m = BookMetadataEntity.builder()
                    .bookId(d.id())
                    .categories(d.categories().stream().map(n -> CategoryEntity.builder().name(n).build()).collect(Collectors.toSet()))
                    .tags(d.tags().stream().map(n -> TagEntity.builder().name(n).build()).collect(Collectors.toSet()))
                    .moods(d.moods().stream().map(n -> MoodEntity.builder().name(n).build()).collect(Collectors.toSet()))
                    .contentRating(d.contentRating())
                    .ageRating(d.ageRating())
                    .build();
            book.setMetadata(m);
        }
        return book;
    }

    private Book toDto(BookDef d) {
        BookMetadata m = d.hasMetadata()
                ? BookMetadata.builder()
                    .bookId(d.id())
                    .categories(d.categories())
                    .tags(d.tags())
                    .moods(d.moods())
                    .contentRating(d.contentRating())
                    .ageRating(d.ageRating())
                    .build()
                : null;
        return Book.builder().id(d.id()).metadata(m).build();
    }

    private ContentRestriction restriction(ContentRestrictionType type, ContentRestrictionMode mode, String value) {
        return ContentRestriction.builder().restrictionType(type).mode(mode).value(value).build();
    }
}
