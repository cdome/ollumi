package org.booklore.perf.feed;

import org.booklore.perf.config.PerfContext;
import org.booklore.perf.config.TestConfig;
import org.booklore.perf.seed.SeedManifest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Feeder factories. All feeders are infinite iterators over session attribute
 * maps, drawing from the {@link SeedManifest} written by {@code seedData}.
 */
public final class Feeders {

    private Feeders() {
    }

    /**
     * Circular feeder over perf-user-0001..perf-user-NNNN (sets "username").
     * The counter is shared across all feeder instances so that concurrent
     * scenarios draw disjoint user ranges (scenario A: users 1..25, B: 26..50, ...).
     */
    private static final AtomicInteger NEXT_USER = new AtomicInteger(0);

    public static Iterator<Map<String, Object>> users() {
        int count = (int) PerfContext.manifest().getLong(SeedManifest.USER_COUNT);
        return infinite(() -> Map.of("username", TestConfig.username(NEXT_USER.getAndIncrement() % count + 1)));
    }

    /** Random existing book id (sets "bookId"). */
    public static Iterator<Map<String, Object>> bookId() {
        long min = PerfContext.manifest().getLong(SeedManifest.BOOK_ID_MIN);
        long max = PerfContext.manifest().getLong(SeedManifest.BOOK_ID_MAX);
        return infinite(() -> Map.of("bookId", ThreadLocalRandom.current().nextLong(min, max + 1)));
    }

    /** Random library id (sets "libraryId"). */
    public static Iterator<Map<String, Object>> libraryId() {
        List<Long> ids = PerfContext.manifest().getLongs(SeedManifest.LIBRARY_IDS);
        return infinite(() -> Map.of("libraryId", randomElement(ids)));
    }

    /** Random author id (sets "authorId"). */
    public static Iterator<Map<String, Object>> authorId() {
        List<Long> ids = PerfContext.manifest().getLongs(SeedManifest.AUTHOR_IDS);
        return infinite(() -> Map.of("authorId", randomElement(ids)));
    }

    /** Random author name (sets "authorName") — for the exact-match authors filter. */
    public static Iterator<Map<String, Object>> authorName() {
        List<String> names = PerfContext.manifest().getStrings(SeedManifest.AUTHOR_NAMES);
        return infinite(() -> Map.of("authorName", randomElement(names)));
    }

    /** Random series name (sets "seriesName") — hyphenated, path-safe. */
    public static Iterator<Map<String, Object>> seriesName() {
        List<String> names = PerfContext.manifest().getStrings(SeedManifest.SERIES_NAMES);
        return infinite(() -> Map.of("seriesName", randomElement(names)));
    }

    /** Random language code (sets "language"). */
    public static Iterator<Map<String, Object>> language() {
        List<String> languages = PerfContext.manifest().getStrings(SeedManifest.LANGUAGES);
        return infinite(() -> Map.of("language", randomElement(languages)));
    }

    /** Random search term known to match titles/authors (sets "searchTerm"). */
    public static Iterator<Map<String, Object>> searchTerm() {
        List<String> terms = PerfContext.manifest().getStrings(SeedManifest.SEARCH_TERMS);
        return infinite(() -> Map.of("searchTerm", randomElement(terms)));
    }

    /** Random page number 0..4 (sets "page") — walks the first pages like a real app. */
    public static Iterator<Map<String, Object>> page() {
        return infinite(() -> Map.of("page", ThreadLocalRandom.current().nextInt(0, 5)));
    }

    /** Random book file type, weighted like the seeded distribution (sets "fileType"). */
    public static Iterator<Map<String, Object>> fileType() {
        return infinite(() -> {
            double r = ThreadLocalRandom.current().nextDouble();
            String type = r < 0.60 ? "EPUB" : r < 0.85 ? "PDF" : r < 0.95 ? "CBX" : "AUDIOBOOK";
            return Map.of("fileType", type);
        });
    }

    /** Random read status, weighted like the seeded distribution (sets "readStatus"). */
    public static Iterator<Map<String, Object>> readStatus() {
        return infinite(() -> {
            double r = ThreadLocalRandom.current().nextDouble();
            String status = r < 0.40 ? "READ" : r < 0.65 ? "READING" : r < 0.85 ? "UNREAD" : r < 0.95 ? "PAUSED" : "PARTIALLY_READ";
            return Map.of("readStatus", status);
        });
    }

    /** Random personal-rating filter range (sets "minRating"). */
    public static Iterator<Map<String, Object>> minRating() {
        return infinite(() -> Map.of("minRating", ThreadLocalRandom.current().nextInt(1, 6)));
    }

    /**
     * Random stats period anchored to the seeded session history (last 2 years):
     * sets "statYear", "statMonth", "statWeek".
     */
    public static Iterator<Map<String, Object>> statPeriod() {
        return infinite(() -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int currentYear = LocalDate.now().getYear();
            Map<String, Object> values = new HashMap<>();
            values.put("statYear", random.nextBoolean() ? currentYear : currentYear - 1);
            values.put("statMonth", random.nextInt(1, 13));
            values.put("statWeek", random.nextInt(1, 53));
            return values;
        });
    }

    // --- helpers ---------------------------------------------------------------

    @FunctionalInterface
    private interface Values {
        Map<String, Object> next();
    }

    private static Iterator<Map<String, Object>> infinite(Values values) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Map<String, Object> next() {
                return values.next();
            }
        };
    }

    private static <T> T randomElement(List<T> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
