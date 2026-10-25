package org.booklore.service.metadata;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookReview;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.repository.jooq.JooqBookReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookReviewUpdateServiceTest {

    private static final long BOOK_ID = 1L;

    @Mock
    private JooqBookReviewRepository bookReviewRepository;

    private BookReviewUpdateService service;

    @BeforeEach
    void setUp() {
        service = new BookReviewUpdateService(bookReviewRepository);
    }

    private BookMetadataEntity entity(boolean reviewsLocked) {
        return BookMetadataEntity.builder()
                .bookId(BOOK_ID)
                .reviewsLocked(reviewsLocked)
                .build();
    }

    private BookReview review(MetadataProvider provider, String name, Instant date) {
        return BookReview.builder()
                .metadataProvider(provider)
                .reviewerName(name)
                .date(date)
                .build();
    }

    /** Captures every BookReview passed to insert() across the test and returns their reviewer names. */
    private Set<String> insertedReviewerNames() {
        ArgumentCaptor<BookReview> captor = ArgumentCaptor.forClass(BookReview.class);
        verify(bookReviewRepository, atLeastOnce()).insert(eq(BOOK_ID), captor.capture());
        Set<String> names = new HashSet<>();
        captor.getAllValues().forEach(r -> names.add(r.getReviewerName()));
        return names;
    }

    @Nested
    class ReviewLockRespect {

        @Test
        void skipsUpdateWhenReviewsLocked() {
            BookMetadataEntity entity = entity(true);
            BookMetadata metadata = BookMetadata.builder()
                    .bookReviews(List.of(review(MetadataProvider.Amazon, "r1", Instant.now())))
                    .build();

            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

            verifyNoInteractions(bookReviewRepository);
        }

        @Test
        void skipsUpdateWhenReviewsLockedIsNull() {
            BookMetadataEntity entity = BookMetadataEntity.builder().bookId(BOOK_ID).reviewsLocked(null).build();
            BookMetadata metadata = BookMetadata.builder()
                    .bookReviews(List.of(review(MetadataProvider.Amazon, "r1", Instant.now())))
                    .build();
            when(bookReviewRepository.findByBookId(BOOK_ID)).thenReturn(List.of());

            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

            assertThat(insertedReviewerNames()).containsExactly("r1");
        }
    }

    @Nested
    class ClearFlags {

        @Test
        void clearsReviewsWhenClearFlagSet() {
            BookMetadataEntity entity = entity(false);

            MetadataClearFlags flags = new MetadataClearFlags();
            flags.setReviews(true);

            service.updateBookReviews(BookMetadata.builder().build(), entity, flags, true);

            verify(bookReviewRepository).deleteByBookId(BOOK_ID);
            verify(bookReviewRepository, never()).insert(anyLong(), any());
        }
    }

    @Nested
    class NullAndEmptyReviewHandling {

        @Test
        void skipsWhenBookReviewsNull() {
            BookMetadataEntity entity = entity(false);
            BookMetadata metadata = BookMetadata.builder().bookReviews(null).build();

            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

            verifyNoInteractions(bookReviewRepository);
        }

        @Test
        void filtersNullReviewsInList() {
            BookMetadataEntity entity = entity(false);
            List<BookReview> reviews = new ArrayList<>();
            reviews.add(null);
            reviews.add(review(MetadataProvider.Amazon, "valid", Instant.now()));
            when(bookReviewRepository.findByBookId(BOOK_ID)).thenReturn(List.of());

            BookMetadata metadata = BookMetadata.builder().bookReviews(reviews).build();
            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

            assertThat(insertedReviewerNames()).containsExactly("valid");
        }

        @Test
        void filtersReviewsWithNullProvider() {
            BookMetadataEntity entity = entity(false);
            BookReview noProvider = BookReview.builder().reviewerName("orphan").build();
            when(bookReviewRepository.findByBookId(BOOK_ID)).thenReturn(List.of());

            BookMetadata metadata = BookMetadata.builder()
                    .bookReviews(List.of(noProvider))
                    .build();
            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

            verify(bookReviewRepository).deleteByBookId(BOOK_ID);
            verify(bookReviewRepository, never()).insert(anyLong(), any());
        }
    }

    @Nested
    class MergeVsReplace {

        @Test
        void mergeAddsToExistingReviews() {
            BookMetadataEntity entity = entity(false);
            BookReview existing = review(MetadataProvider.Amazon, "old", Instant.now());
            when(bookReviewRepository.findByBookId(BOOK_ID)).thenReturn(List.of(existing));

            BookMetadata metadata = BookMetadata.builder()
                    .bookReviews(List.of(review(MetadataProvider.GoodReads, "new", Instant.now())))
                    .build();
            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

            assertThat(insertedReviewerNames()).containsExactlyInAnyOrder("old", "new");
        }

        @Test
        void replaceRemovesExistingReviews() {
            BookMetadataEntity entity = entity(false);

            BookMetadata metadata = BookMetadata.builder()
                    .bookReviews(List.of(review(MetadataProvider.GoodReads, "replacement", Instant.now())))
                    .build();
            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), false);

            verify(bookReviewRepository, never()).findByBookId(anyLong());
            assertThat(insertedReviewerNames()).containsExactly("replacement");
        }
    }

    @Nested
    class PerProviderLimit {

        @Test
        void limitsToFiveReviewsPerProvider() {
            BookMetadataEntity entity = entity(false);
            Instant base = Instant.now();
            when(bookReviewRepository.findByBookId(BOOK_ID)).thenReturn(List.of());

            List<BookReview> sevenReviews = IntStream.range(0, 7)
                    .mapToObj(i -> review(MetadataProvider.Amazon, "r" + i, base.minus(i, ChronoUnit.DAYS)))
                    .toList();

            BookMetadata metadata = BookMetadata.builder().bookReviews(sevenReviews).build();
            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

            assertThat(insertedReviewerNames()).hasSize(5);
        }

        @Test
        void limitsApplyPerProviderIndependently() {
            BookMetadataEntity entity = entity(false);
            Instant base = Instant.now();
            when(bookReviewRepository.findByBookId(BOOK_ID)).thenReturn(List.of());

            List<BookReview> reviews = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                reviews.add(review(MetadataProvider.Amazon, "az" + i, base.minus(i, ChronoUnit.DAYS)));
            }
            for (int i = 0; i < 6; i++) {
                reviews.add(review(MetadataProvider.GoodReads, "gr" + i, base.minus(i, ChronoUnit.DAYS)));
            }

            BookMetadata metadata = BookMetadata.builder().bookReviews(reviews).build();
            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

            ArgumentCaptor<BookReview> captor = ArgumentCaptor.forClass(BookReview.class);
            verify(bookReviewRepository, times(10)).insert(eq(BOOK_ID), captor.capture());

            long amazonCount = captor.getAllValues().stream()
                    .filter(r -> r.getMetadataProvider() == MetadataProvider.Amazon)
                    .count();
            long goodreadsCount = captor.getAllValues().stream()
                    .filter(r -> r.getMetadataProvider() == MetadataProvider.GoodReads)
                    .count();

            assertThat(amazonCount).isEqualTo(5);
            assertThat(goodreadsCount).isEqualTo(5);
        }

        @Test
        void keepsMostRecentReviewsWhenLimitApplied() {
            BookMetadataEntity entity = entity(false);
            Instant base = Instant.now();
            when(bookReviewRepository.findByBookId(BOOK_ID)).thenReturn(List.of());

            List<BookReview> reviews = IntStream.range(0, 7)
                    .mapToObj(i -> review(MetadataProvider.Amazon, "r" + i, base.minus(i, ChronoUnit.DAYS)))
                    .toList();

            BookMetadata metadata = BookMetadata.builder().bookReviews(reviews).build();
            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

            Set<String> keptNames = insertedReviewerNames();

            assertThat(keptNames).contains("r0", "r1", "r2", "r3", "r4");
            assertThat(keptNames).doesNotContain("r6");
        }

        @Test
        void handlesNullDatesInLimitSorting() {
            BookMetadataEntity entity = entity(false);
            when(bookReviewRepository.findByBookId(BOOK_ID)).thenReturn(List.of());

            List<BookReview> reviews = new ArrayList<>();
            reviews.add(review(MetadataProvider.Amazon, "dated", Instant.now()));
            reviews.add(review(MetadataProvider.Amazon, "undated", null));

            BookMetadata metadata = BookMetadata.builder().bookReviews(reviews).build();
            service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

            assertThat(insertedReviewerNames()).containsExactlyInAnyOrder("dated", "undated");
        }
    }

    @Nested
    class AddReviewsToBook {

        @Test
        void addsReviewsViaConvenienceMethod() {
            BookMetadataEntity entity = entity(false);
            when(bookReviewRepository.findByBookId(BOOK_ID)).thenReturn(List.of());
            List<BookReview> reviews = List.of(
                    review(MetadataProvider.Amazon, "r1", Instant.now()),
                    review(MetadataProvider.GoodReads, "r2", Instant.now())
            );

            service.addReviewsToBook(reviews, entity);

            assertThat(insertedReviewerNames()).containsExactlyInAnyOrder("r1", "r2");
        }

        @Test
        void addReviewsToBookRespectsLock() {
            BookMetadataEntity entity = entity(true);

            service.addReviewsToBook(
                    List.of(review(MetadataProvider.Amazon, "r1", Instant.now())),
                    entity
            );

            verifyNoInteractions(bookReviewRepository);
        }
    }

    @Test
    void reviewEntityFieldsAreMappedCorrectly() {
        BookMetadataEntity entity = entity(false);
        when(bookReviewRepository.findByBookId(BOOK_ID)).thenReturn(List.of());
        Instant date = Instant.now();
        BookReview review = BookReview.builder()
                .metadataProvider(MetadataProvider.Hardcover)
                .reviewerName("Jane")
                .title("Great Book")
                .rating(4.5f)
                .date(date)
                .body("Loved it")
                .spoiler(true)
                .followersCount(100)
                .textReviewsCount(50)
                .country("US")
                .build();

        BookMetadata metadata = BookMetadata.builder().bookReviews(List.of(review)).build();
        service.updateBookReviews(metadata, entity, new MetadataClearFlags(), true);

        ArgumentCaptor<BookReview> captor = ArgumentCaptor.forClass(BookReview.class);
        verify(bookReviewRepository).insert(eq(BOOK_ID), captor.capture());
        BookReview inserted = captor.getValue();

        assertThat(inserted.getMetadataProvider()).isEqualTo(MetadataProvider.Hardcover);
        assertThat(inserted.getReviewerName()).isEqualTo("Jane");
        assertThat(inserted.getTitle()).isEqualTo("Great Book");
        assertThat(inserted.getRating()).isEqualTo(4.5f);
        assertThat(inserted.getDate()).isEqualTo(date);
        assertThat(inserted.getBody()).isEqualTo("Loved it");
        assertThat(inserted.getSpoiler()).isTrue();
        assertThat(inserted.getFollowersCount()).isEqualTo(100);
        assertThat(inserted.getTextReviewsCount()).isEqualTo(50);
        assertThat(inserted.getCountry()).isEqualTo("US");
    }
}
