package org.booklore.service.metadata.writer;

import org.booklore.model.entity.BookMetadataEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MetadataCopyHelperTest {

    private BookMetadataEntity metadata;

    @BeforeEach
    void setUp() {
        metadata = new BookMetadataEntity();
    }

    @Nested
    class MoodsTests {
        // moods is no longer a Hibernate-managed collection: the caller resolves current mood names
        // via jOOQ and passes them straight through, so copyMoods just gates on lock/clear now -
        // filtering blank/null names moved to the writers (BookMetadataUpdater/BookCreatorService).
        @Test
        void copyMoods_whenNotLocked_callsConsumer() {
            metadata.setMoodsLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Set<String>> result = new AtomicReference<>();

            helper.copyMoods(false, Set.of("Dark", "Atmospheric"), result::set);

            assertNotNull(result.get());
            assertEquals(2, result.get().size());
            assertTrue(result.get().contains("Dark"));
            assertTrue(result.get().contains("Atmospheric"));
        }

        @Test
        void copyMoods_whenLocked_doesNotCallConsumer() {
            metadata.setMoodsLocked(true);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Set<String>> result = new AtomicReference<>();

            helper.copyMoods(false, Set.of("Dark"), result::set);

            assertNull(result.get());
        }

        @Test
        void copyMoods_whenClear_passesEmptySet() {
            metadata.setMoodsLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Set<String>> result = new AtomicReference<>();

            helper.copyMoods(true, Set.of("Dark"), result::set);

            assertNotNull(result.get());
            assertTrue(result.get().isEmpty());
        }

        @Test
        void copyMoods_whenCurrentNamesNull_doesNotCallConsumer() {
            metadata.setMoodsLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Set<String>> result = new AtomicReference<>();

            helper.copyMoods(false, null, result::set);

            assertNull(result.get());
        }
    }

    @Nested
    class TagsTests {
        // Same reasoning as MoodsTests: filtering blank/null names moved to the writers.
        @Test
        void copyTags_whenNotLocked_callsConsumer() {
            metadata.setTagsLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Set<String>> result = new AtomicReference<>();

            helper.copyTags(false, Set.of("Fantasy", "Epic"), result::set);

            assertNotNull(result.get());
            assertEquals(2, result.get().size());
            assertTrue(result.get().contains("Fantasy"));
            assertTrue(result.get().contains("Epic"));
        }

        @Test
        void copyTags_whenLocked_doesNotCallConsumer() {
            metadata.setTagsLocked(true);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Set<String>> result = new AtomicReference<>();

            helper.copyTags(false, Set.of("Fantasy"), result::set);

            assertNull(result.get());
        }
    }

    @Nested
    class RatingTests {
        @Test
        void copyRating_whenPresent_callsConsumer() {
            metadata.setRating(8.5);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Double> result = new AtomicReference<>();

            helper.copyRating(false, result::set);

            assertEquals(8.5, result.get());
        }

        @Test
        void copyRating_whenClear_passesNull() {
            metadata.setRating(8.5);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Double> result = new AtomicReference<>(99.0);

            helper.copyRating(true, result::set);

            assertNull(result.get());
        }

        @Test
        void copyAmazonRating_whenNotLocked_callsConsumer() {
            metadata.setAmazonRating(4.5);
            metadata.setAmazonRatingLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Double> result = new AtomicReference<>();

            helper.copyAmazonRating(false, result::set);

            assertEquals(4.5, result.get());
        }

        @Test
        void copyAmazonRating_whenLocked_doesNotCallConsumer() {
            metadata.setAmazonRating(4.5);
            metadata.setAmazonRatingLocked(true);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Double> result = new AtomicReference<>();

            helper.copyAmazonRating(false, result::set);

            assertNull(result.get());
        }

        @Test
        void copyGoodreadsRating_whenNotLocked_callsConsumer() {
            metadata.setGoodreadsRating(4.2);
            metadata.setGoodreadsRatingLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Double> result = new AtomicReference<>();

            helper.copyGoodreadsRating(false, result::set);

            assertEquals(4.2, result.get());
        }

        @Test
        void copyHardcoverRating_whenNotLocked_callsConsumer() {
            metadata.setHardcoverRating(3.8);
            metadata.setHardcoverRatingLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Double> result = new AtomicReference<>();

            helper.copyHardcoverRating(false, result::set);

            assertEquals(3.8, result.get());
        }

        @Test
        void copyLubimyczytacRating_whenNotLocked_callsConsumer() {
            metadata.setLubimyczytacRating(7.5);
            metadata.setLubimyczytacRatingLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Double> result = new AtomicReference<>();

            helper.copyLubimyczytacRating(false, result::set);

            assertEquals(7.5, result.get());
        }

        @Test
        void copyRanobedbRating_whenNotLocked_callsConsumer() {
            metadata.setRanobedbRating(8.0);
            metadata.setRanobedbRatingLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<Double> result = new AtomicReference<>();

            helper.copyRanobedbRating(false, result::set);

            assertEquals(8.0, result.get());
        }
    }

    @Nested
    class IdentifierTests {
        @Test
        void copyLubimyczytacId_whenNotLocked_callsConsumer() {
            metadata.setLubimyczytacId("12345");
            metadata.setLubimyczytacIdLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<String> result = new AtomicReference<>();

            helper.copyLubimyczytacId(false, result::set);

            assertEquals("12345", result.get());
        }

        @Test
        void copyLubimyczytacId_whenLocked_doesNotCallConsumer() {
            metadata.setLubimyczytacId("12345");
            metadata.setLubimyczytacIdLocked(true);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<String> result = new AtomicReference<>();

            helper.copyLubimyczytacId(false, result::set);

            assertNull(result.get());
        }

        @Test
        void copyHardcoverBookId_whenNotLocked_callsConsumer() {
            metadata.setHardcoverBookId("98765");
            metadata.setHardcoverBookIdLocked(false);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<String> result = new AtomicReference<>();

            helper.copyHardcoverBookId(false, result::set);

            assertEquals("98765", result.get());
        }

        @Test
        void copyHardcoverBookId_whenLocked_doesNotCallConsumer() {
            metadata.setHardcoverBookId("98765");
            metadata.setHardcoverBookIdLocked(true);

            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            AtomicReference<String> result = new AtomicReference<>();

            helper.copyHardcoverBookId(false, result::set);

            assertNull(result.get());
        }
    }
}
