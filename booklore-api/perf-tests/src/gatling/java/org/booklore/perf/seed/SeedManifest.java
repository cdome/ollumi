package org.booklore.perf.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Describes the seeded dataset so Gatling feeders know which IDs/names exist.
 * Written by {@link DataSeeder} after a successful seed, read by the simulations.
 * Stored as a plain properties file (no JSON dependency needed).
 */
public final class SeedManifest {

    public static final Path PATH = Path.of("build", "seed-manifest.properties");

    /** Unit separator — cannot collide with characters in names/terms. */
    private static final String STRING_SEPARATOR = String.valueOf((char) 31); // 0x1F unit separator

    private final Properties props = new Properties();

    private SeedManifest() {
    }

    public static SeedManifest create() {
        return new SeedManifest();
    }

    public static SeedManifest load() {
        SeedManifest manifest = new SeedManifest();
        if (!Files.exists(PATH)) {
            throw new IllegalStateException("Seed manifest not found at " + PATH.toAbsolutePath()
                    + " — run './gradlew seedData' first.");
        }
        try (InputStream in = Files.newInputStream(PATH)) {
            manifest.props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read seed manifest " + PATH.toAbsolutePath(), e);
        }
        return manifest;
    }

    public void store() {
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream out = Files.newOutputStream(PATH)) {
                props.store(out, "booklore perf test seed manifest");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write seed manifest " + PATH.toAbsolutePath(), e);
        }
    }

    public static void delete() {
        try {
            Files.deleteIfExists(PATH);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete seed manifest " + PATH.toAbsolutePath(), e);
        }
    }

    // --- generic accessors ---------------------------------------------------

    public SeedManifest set(String key, String value) {
        props.setProperty(key, value);
        return this;
    }

    public SeedManifest setLong(String key, long value) {
        return set(key, String.valueOf(value));
    }

    public SeedManifest setLongs(String key, List<Long> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        return set(key, sb.toString());
    }

    public SeedManifest setStrings(String key, List<String> values) {
        return set(key, String.join(STRING_SEPARATOR, values));
    }

    public long getLong(String key) {
        return Long.parseLong(required(key));
    }

    public List<Long> getLongs(String key) {
        return Arrays.stream(required(key).split(",")).map(String::trim).map(Long::parseLong).toList();
    }

    public List<String> getStrings(String key) {
        return Arrays.stream(required(key).split(STRING_SEPARATOR)).toList();
    }

    private String required(String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Seed manifest is missing key '" + key + "' — re-run './gradlew seedData'.");
        }
        return value;
    }

    // --- well-known keys -----------------------------------------------------

    public static final String LIBRARY_IDS = "libraryIds";
    public static final String BOOK_ID_MIN = "bookIdMin";
    public static final String BOOK_ID_MAX = "bookIdMax";
    public static final String SHELF_IDS = "shelfIds";
    public static final String FAVORITES_SHELF_IDS = "favoritesShelfIds";
    public static final String AUTHOR_IDS = "authorIds";
    public static final String AUTHOR_NAMES = "authorNames";
    public static final String SERIES_NAMES = "seriesNames";
    public static final String LANGUAGES = "languages";
    public static final String SEARCH_TERMS = "searchTerms";
    public static final String USER_COUNT = "userCount";
    public static final String BOOK_COUNT = "bookCount";
}
