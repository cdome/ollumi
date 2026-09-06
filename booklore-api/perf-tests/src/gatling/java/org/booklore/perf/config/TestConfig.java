package org.booklore.perf.config;

import java.time.Duration;

/**
 * Central configuration for the perf tests.
 *
 * Every value can be overridden with a -D system property (e.g.
 * {@code -Dperf.baseUrl=https://staging:6060}) or the matching env var
 * (e.g. {@code PERF_BASE_URL}). Defaults target the local dev setup:
 * app on localhost:6060, MariaDB on localhost:3366 (see dev.docker-compose.yml).
 */
public final class TestConfig {

    private TestConfig() {
    }

    // --- Target application ---
    public static final String BASE_URL = prop("perf.baseUrl", "PERF_BASE_URL", "http://localhost:6060");

    // --- Target database (used by the JDBC seeder only) ---
    public static final String DB_URL = prop("perf.db.url", "PERF_DB_URL",
            "jdbc:mariadb://localhost:3366/booklore"
                    + "?rewriteBatchedStatements=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    public static final String DB_USER = prop("perf.db.user", "PERF_DB_USER", "booklore");
    public static final String DB_PASSWORD = prop("perf.db.password", "PERF_DB_PASSWORD", "booklore");

    // --- Dataset size ---
    public static final int USER_COUNT = intProp("perf.userCount", "PERF_USER_COUNT", 1_000);
    public static final int BOOK_COUNT = intProp("perf.bookCount", "PERF_BOOK_COUNT", 10_000);
    public static final int LIBRARY_COUNT = intProp("perf.libraryCount", "PERF_LIBRARY_COUNT", 3);
    public static final int AUTHOR_COUNT = intProp("perf.authorCount", "PERF_AUTHOR_COUNT", 500);
    public static final int CATEGORY_COUNT = intProp("perf.categoryCount", "PERF_CATEGORY_COUNT", 50);
    public static final int TAG_COUNT = intProp("perf.tagCount", "PERF_TAG_COUNT", 100);
    public static final int PROGRESS_BOOKS_PER_USER = intProp("perf.progressBooksPerUser", "PERF_PROGRESS_BOOKS_PER_USER", 50);
    public static final int SESSIONS_PER_USER = intProp("perf.sessionsPerUser", "PERF_SESSIONS_PER_USER", 75);

    // --- Load profile ---
    public static final int CONCURRENT_USERS = intProp("perf.concurrentUsers", "PERF_CONCURRENT_USERS", 100);
    public static final Duration RAMP_UP = durationProp("perf.rampUp", "PERF_RAMP_UP", Duration.ofMinutes(2));
    public static final Duration DURATION = durationProp("perf.duration", "PERF_DURATION", Duration.ofMinutes(10));

    // --- Seeded credentials ---
    /** All perf users share one password (one BCrypt hash computed once, reused for every row). */
    public static final String PERF_USER_PASSWORD = prop("perf.user.password", "PERF_USER_PASSWORD", "PerfUser-Passw0rd!");
    public static final String USERNAME_PREFIX = "perf-user-";
    public static final String LIBRARY_NAME_PREFIX = "perf-library-";

    /** Bootstrap admin (created via POST /api/v1/setup only when the users table is empty). */
    public static final String ADMIN_USERNAME = prop("perf.admin.username", "PERF_ADMIN_USERNAME", "perf-admin");
    public static final String ADMIN_PASSWORD = prop("perf.admin.password", "PERF_ADMIN_PASSWORD", "PerfAdmin-Passw0rd!");

    /** Seconds to wait for the app healthcheck before seeding. */
    public static final int WAIT_FOR_APP_SECONDS = intProp("perf.waitForAppSeconds", "PERF_WAIT_FOR_APP_SECONDS", 60);

    // --- Assertion thresholds (ms) ---
    // Calibrated against the local-dev baseline run (10k books, 100 concurrent users, app + MariaDB
    // on one machine): full list p95 ~6.7s, paged/filtered p95 ~3.5s. Tighten via -D when optimizing.
    public static final int P95_PAGED_MS = intProp("perf.threshold.pagedMs", "PERF_THRESHOLD_PAGED_MS", 5_000);
    public static final int P95_FULL_LIST_MS = intProp("perf.threshold.fullListMs", "PERF_THRESHOLD_FULL_LIST_MS", 8_000);
    public static final double MIN_SUCCESS_PERCENT = doubleProp("perf.threshold.successPercent", "PERF_THRESHOLD_SUCCESS_PERCENT", 99.0);

    // --- Deterministic data generation ---
    public static final long RANDOM_SEED = Long.parseLong(prop("perf.randomSeed", "PERF_RANDOM_SEED", "42"));

    public static String username(int index) {
        return USERNAME_PREFIX + String.format("%04d", index);
    }

    private static String prop(String sysProp, String envVar, String defaultValue) {
        String value = System.getProperty(sysProp);
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private static int intProp(String sysProp, String envVar, int defaultValue) {
        return Integer.parseInt(prop(sysProp, envVar, String.valueOf(defaultValue)));
    }

    private static double doubleProp(String sysProp, String envVar, double defaultValue) {
        return Double.parseDouble(prop(sysProp, envVar, String.valueOf(defaultValue)));
    }

    /** Parses durations like "30s", "2m", "1h" (ISO-8601 "PT2M" also accepted). */
    private static Duration durationProp(String sysProp, String envVar, Duration defaultValue) {
        String raw = prop(sysProp, envVar, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            String lower = raw.toLowerCase().trim();
            if (lower.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(lower.substring(0, lower.length() - 2)));
            }
            if (lower.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(lower.substring(0, lower.length() - 1)));
            }
            if (lower.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(lower.substring(0, lower.length() - 1)));
            }
            if (lower.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(lower.substring(0, lower.length() - 1)));
            }
            return Duration.parse(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid duration '" + raw + "' for " + sysProp
                    + " (expected e.g. 30s, 2m, 1h or ISO-8601)", e);
        }
    }
}
