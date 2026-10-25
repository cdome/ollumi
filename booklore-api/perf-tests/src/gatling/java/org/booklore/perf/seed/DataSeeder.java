package org.booklore.perf.seed;

import org.booklore.perf.chain.AuthSupport;
import org.booklore.perf.config.AppHttp;
import org.booklore.perf.config.TestConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Seeds the target database with the perf dataset:
 * <ul>
 *   <li>{@code perf.userCount} users (one shared BCrypt hash, computed once)</li>
 *   <li>{@code perf.bookCount} books with metadata, one digital file each, authors/categories/tags</li>
 *   <li>libraries, shelves + shelf assignments</li>
 *   <li>per-user reading progress and ~2 years of reading sessions</li>
 * </ul>
 *
 * IDs are allocated explicitly (MAX(id)+1 per table) instead of relying on
 * getGeneratedKeys — fully deterministic and safe with batched statements.
 * Re-runnable only after {@link DataCleaner}; aborts early if perf data exists.
 */
public final class DataSeeder {

    private static final String[] LANGUAGES = {"en", "de", "fr", "es", "it"};
    private static final double[] LANGUAGE_WEIGHTS = {0.60, 0.15, 0.10, 0.10, 0.05};

    private static final String[] ADJECTIVES = {
            "Silent", "Burning", "Hidden", "Golden", "Broken", "Scarlet", "Hollow", "Ancient", "Electric", "Frozen",
            "Crimson", "Velvet", "Iron", "Paper", "Glass", "Midnight", "Wandering", "Secret", "Lonely", "Radiant",
            "Distant", "Emerald", "Ashen", "Wild", "Quiet", "Silver", "Ebony", "Fragile", "Endless", "Forgotten",
            "Sapphire", "Whispering", "Obsidian", "Luminous", "Restless", "Ivory", "Shadowy", "Amber", "Scarlet", "Brass"
    };

    private static final String[] NOUNS = {
            "River", "Mountain", "Garden", "Empire", "Mirror", "Throne", "Compass", "Lantern", "Voyage", "Echo",
            "Harbor", "Forest", "Clock", "Labyrinth", "Sparrow", "Winter", "Orchard", "Tide", "Archive", "Bridge",
            "Desert", "Symphony", "Map", "Tower", "Storm", "Meadow", "Cipher", "Quill", "Horizon", "Ashes",
            "Pilgrim", "Mask", "Loom", "Reef", "Crown", "Valley", "Signal", "Ember", "Lighthouse", "Veil",
            "Anchor", "Comet", "Citadel", "Root", "Flame", "Wolf", "Sextant", "Monsoon", "Key", "Island"
    };

    private static final String[] TITLE_PATTERNS = {
            "The %s %s", "%s of %s", "The %s of the %s", "A %s %s", "The %s and the %s", "%s %s", "Beneath the %s %s"
    };

    private static final String[] PUBLISHERS = {
            "Perf House", "Test Press", "Benchmark Books", "Load Publishing", "Gatling & Sons", "Latency Lane",
            "Throughput Press", "Percentile Publishing", "Sampler House", "Pager & Co"
    };

    private static final Random RANDOM = new Random(TestConfig.RANDOM_SEED);

    public static void main(String[] args) throws Exception {
        long startedAt = System.currentTimeMillis();
        System.out.println("[DataSeeder] Target app: " + TestConfig.BASE_URL);
        System.out.println("[DataSeeder] Target DB:  " + TestConfig.DB_URL);
        System.out.println("[DataSeeder] Dataset: " + TestConfig.BOOK_COUNT + " books, " + TestConfig.USER_COUNT + " users, "
                + TestConfig.LIBRARY_COUNT + " libraries, " + TestConfig.SESSIONS_PER_USER + " sessions/user");

        waitForApp();
        ensureAdminBestEffort();

        try (Connection db = DriverManager.getConnection(TestConfig.DB_URL, TestConfig.DB_USER, TestConfig.DB_PASSWORD)) {
            db.setAutoCommit(false);
            try {
                abortIfAlreadySeeded(db);

                List<Long> authorIds = seedDictionaries(db, "author", "Perf Author ", TestConfig.AUTHOR_COUNT);
                List<Long> categoryIds = seedDictionaries(db, "category", "Perf Category ", TestConfig.CATEGORY_COUNT);
                List<Long> tagIds = seedDictionaries(db, "tag", "Perf Tag ", TestConfig.TAG_COUNT);
                db.commit();
                System.out.println("[DataSeeder] Dictionaries seeded: " + authorIds.size() + " authors, "
                        + categoryIds.size() + " categories, " + tagIds.size() + " tags");

                List<Long> libraryIds = new ArrayList<>();
                List<Long> libraryPathIds = new ArrayList<>();
                seedLibraries(db, libraryIds, libraryPathIds);
                db.commit();
                System.out.println("[DataSeeder] Libraries seeded: " + libraryIds);

                List<Long> userIds = seedUsers(db, libraryIds);
                db.commit();
                System.out.println("[DataSeeder] Users seeded: " + userIds.size());

                ShelfIds shelfIds = seedShelves(db, userIds);
                db.commit();
                System.out.println("[DataSeeder] Shelves seeded: " + shelfIds.allShelfIds.size());

                BookIds bookIds = seedBooks(db, libraryIds, libraryPathIds, authorIds, categoryIds, tagIds);
                db.commit();
                System.out.println("[DataSeeder] Books seeded: " + bookIds.bookIds.size());

                seedShelfAssignments(db, userIds, shelfIds, bookIds.bookIds);
                db.commit();

                seedProgress(db, userIds, bookIds);
                db.commit();
                System.out.println("[DataSeeder] Reading progress seeded");

                seedReadingSessions(db, userIds, bookIds);
                db.commit();
                System.out.println("[DataSeeder] Reading sessions seeded");

                writeManifest(libraryIds, bookIds, shelfIds, authorIds);
                System.out.println("[DataSeeder] Seed manifest written to " + SeedManifest.PATH.toAbsolutePath());
            } catch (Exception e) {
                db.rollback();
                System.err.println("[DataSeeder] FAILED, rolled back current batch. "
                        + "Run './gradlew cleanData' before retrying. Cause: " + e.getMessage());
                throw e;
            }
        }

        validateLogin();

        System.out.println("[DataSeeder] DONE in " + (System.currentTimeMillis() - startedAt) + " ms. "
                + "You can now run './gradlew gatlingRun-SmokeSimulation' or './gradlew gatlingRun-MixedLoadSimulation'.");
    }

    // ------------------------------------------------------------------
    // Phase 0: app bootstrap
    // ------------------------------------------------------------------

    private static void waitForApp() {
        System.out.println("[DataSeeder] Waiting for app healthcheck at " + TestConfig.BASE_URL + "/api/v1/healthcheck ...");
        long deadline = System.currentTimeMillis() + TestConfig.WAIT_FOR_APP_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                AppHttp.Response response = AppHttp.get("/api/v1/healthcheck");
                if (response.is2xx()) {
                    System.out.println("[DataSeeder] App is up.");
                    return;
                }
            } catch (RuntimeException ignored) {
                // app not up yet
            }
            sleep(1_000);
        }
        throw new IllegalStateException("App did not become healthy within " + TestConfig.WAIT_FOR_APP_SECONDS
                + "s at " + TestConfig.BASE_URL);
    }

    /**
     * Best-effort creation of the first admin via the public setup wizard.
     * Not strictly required for the load tests (perf users are inserted via JDBC),
     * but leaves the instance in a browsable state when it started empty.
     */
    private static void ensureAdminBestEffort() {
        String body = "{\"username\":\"" + TestConfig.ADMIN_USERNAME + "\","
                + "\"email\":\"perf-admin@booklore-perf.local\","
                + "\"name\":\"Perf Admin\","
                + "\"password\":\"" + AppHttp.jsonEscape(TestConfig.ADMIN_PASSWORD) + "\"}";
        try {
            AppHttp.Response response = AppHttp.postJson("/api/v1/setup", body);
            if (response.is2xx()) {
                System.out.println("[DataSeeder] Bootstrap admin '" + TestConfig.ADMIN_USERNAME + "' created via /api/v1/setup");
            } else {
                System.out.println("[DataSeeder] Setup wizard returned HTTP " + response.status()
                        + " (instance likely already configured) — continuing.");
            }
        } catch (RuntimeException e) {
            System.out.println("[DataSeeder] Setup wizard call failed (" + e.getMessage() + ") — continuing.");
        }
    }

    // ------------------------------------------------------------------
    // Guards & id allocation
    // ------------------------------------------------------------------

    private static void abortIfAlreadySeeded(Connection db) throws SQLException {
        long perfUsers = countWhere(db, "users WHERE username LIKE 'perf-user-%'");
        long perfLibraries = countWhere(db, "library WHERE name LIKE '" + TestConfig.LIBRARY_NAME_PREFIX + "%'");
        if (perfUsers > 0 || perfLibraries > 0) {
            throw new IllegalStateException("Perf data already present (perf users: " + perfUsers
                    + ", perf libraries: " + perfLibraries + "). Run './gradlew cleanData' first.");
        }
    }

    private static long countWhere(Connection db, String tableAndCondition) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableAndCondition)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long nextId(Connection db, String table) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ------------------------------------------------------------------
    // Seeding sections
    // ------------------------------------------------------------------

    private static List<Long> seedDictionaries(Connection db, String table, String namePrefix, int count) throws SQLException {
        List<Long> ids = new ArrayList<>(count);
        long nextId = nextId(db, table);
        try (BatchWriter writer = new BatchWriter(db, "INSERT INTO " + table + " (id, name) VALUES (?, ?)")) {
            for (int i = 0; i < count; i++) {
                long id = nextId + i;
                writer.ps().setLong(1, id);
                writer.ps().setString(2, namePrefix + String.format("%04d", i + 1));
                writer.add();
                ids.add(id);
            }
            writer.flush();
        }
        return ids;
    }

    private static void seedLibraries(Connection db, List<Long> libraryIds, List<Long> libraryPathIds) throws SQLException {
        long nextLibraryId = nextId(db, "library");
        long nextPathId = nextId(db, "library_path");
        try (BatchWriter libraries = new BatchWriter(db,
                     "INSERT INTO library (id, name, watch, icon, icon_type, organization_mode, metadata_source)"
                             + " VALUES (?, ?, 0, NULL, NULL, 'AUTO_DETECT', 'EMBEDDED')");
             BatchWriter paths = new BatchWriter(db,
                     "INSERT INTO library_path (id, library_id, path) VALUES (?, ?, ?)")) {
            for (int i = 0; i < TestConfig.LIBRARY_COUNT; i++) {
                long libraryId = nextLibraryId + i;
                long pathId = nextPathId + i;
                libraries.ps().setLong(1, libraryId);
                libraries.ps().setString(2, TestConfig.LIBRARY_NAME_PREFIX + (i + 1));
                libraries.add();
                paths.ps().setLong(1, pathId);
                paths.ps().setLong(2, libraryId);
                paths.ps().setString(3, "/perf-data/library-" + (i + 1));
                paths.add();
                libraryIds.add(libraryId);
                libraryPathIds.add(pathId);
            }
            libraries.flush();
            paths.flush();
        }
    }

    private static List<Long> seedUsers(Connection db, List<Long> libraryIds) throws SQLException {
        int count = TestConfig.USER_COUNT;
        List<Long> ids = new ArrayList<>(count);
        long nextUserId = nextId(db, "users");

        System.out.println("[DataSeeder] Computing shared BCrypt hash for " + count + " users (one-time cost)...");
        String passwordHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode(TestConfig.PERF_USER_PASSWORD);
        Timestamp createdAt = Timestamp.from(Instant.now().minus(365 + RANDOM.nextInt(365), ChronoUnit.DAYS));

        try (BatchWriter users = new BatchWriter(db,
                     "INSERT INTO users (id, username, password_hash, is_default_password, name, email, provisioning_method, created_at)"
                             + " VALUES (?, ?, ?, 0, ?, ?, 'LOCAL', ?)");
             BatchWriter permissions = new BatchWriter(db,
                     "INSERT INTO user_permissions (user_id, permission_admin, permission_access_user_stats) VALUES (?, 0, 1)");
             BatchWriter libraryMapping = new BatchWriter(db,
                     "INSERT INTO user_library_mapping (user_id, library_id) VALUES (?, ?)")) {
            for (int i = 0; i < count; i++) {
                long userId = nextUserId + i;
                String username = TestConfig.username(i + 1);
                users.ps().setLong(1, userId);
                users.ps().setString(2, username);
                users.ps().setString(3, passwordHash);
                users.ps().setString(4, "Perf User " + String.format("%04d", i + 1));
                users.ps().setString(5, username + "@booklore-perf.local");
                users.ps().setTimestamp(6, createdAt);
                users.add();

                permissions.ps().setLong(1, userId);
                permissions.add();

                for (Long libraryId : libraryIds) {
                    libraryMapping.ps().setLong(1, userId);
                    libraryMapping.ps().setLong(2, libraryId);
                    libraryMapping.add();
                }
                ids.add(userId);
                if ((i + 1) % 200 == 0 || i + 1 == count) {
                    // ordered flush: parents (users) before children (permissions, mappings)
                    users.flush();
                    permissions.flush();
                    libraryMapping.flush();
                    System.out.println("[DataSeeder]   users: " + (i + 1) + "/" + count);
                }
            }
            users.flush();
            permissions.flush();
            libraryMapping.flush();
        }
        return ids;
    }

    /** Shelf ids per user: index 0 = private "Favorites" (always), 1..n = extra shelves. */
    private record ShelfIds(Map<Long, List<Long>> byUserId, List<Long> favoritesShelfIds, List<Long> allShelfIds) {
    }

    private static ShelfIds seedShelves(Connection db, List<Long> userIds) throws SQLException {
        long id = nextId(db, "shelf");
        Map<Long, List<Long>> byUserId = new HashMap<>(userIds.size() * 2);
        List<Long> favoritesShelfIds = new ArrayList<>(userIds.size());
        List<Long> allShelfIds = new ArrayList<>();
        try (BatchWriter shelves = new BatchWriter(db,
                "INSERT INTO shelf (id, user_id, name, icon, icon_type, is_public) VALUES (?, ?, ?, NULL, NULL, ?)")) {
            for (Long userId : userIds) {
                List<Long> userShelfIds = new ArrayList<>(3);
                shelves.ps().setLong(1, id);
                shelves.ps().setLong(2, userId);
                shelves.ps().setString(3, "Favorites");
                shelves.ps().setBoolean(4, false);
                shelves.add();
                userShelfIds.add(id);
                favoritesShelfIds.add(id);
                allShelfIds.add(id);
                id++;
                if (RANDOM.nextDouble() < 0.25) {
                    shelves.ps().setLong(1, id);
                    shelves.ps().setLong(2, userId);
                    shelves.ps().setString(3, "Reading List");
                    shelves.ps().setBoolean(4, false);
                    shelves.add();
                    userShelfIds.add(id);
                    allShelfIds.add(id);
                    id++;
                }
                if (RANDOM.nextDouble() < 0.10) {
                    shelves.ps().setLong(1, id);
                    shelves.ps().setLong(2, userId);
                    shelves.ps().setString(3, "Recommended");
                    shelves.ps().setBoolean(4, true);
                    shelves.add();
                    userShelfIds.add(id);
                    allShelfIds.add(id);
                    id++;
                }
                byUserId.put(userId, userShelfIds);
            }
            shelves.flush();
        }
        return new ShelfIds(byUserId, favoritesShelfIds, allShelfIds);
    }

    private record BookIds(List<Long> bookIds, Map<Long, Long> fileIdByBookId, Map<Long, String> fileTypeByBookId) {
    }

    private static BookIds seedBooks(Connection db, List<Long> libraryIds, List<Long> libraryPathIds,
                                     List<Long> authorIds, List<Long> categoryIds, List<Long> tagIds) throws SQLException {
        int bookCount = TestConfig.BOOK_COUNT;
        long nextBookId = nextId(db, "book");
        long nextFileId = nextId(db, "book_file");

        List<Long> bookIds = new ArrayList<>(bookCount);
        Map<Long, Long> fileIdByBookId = new HashMap<>(bookCount * 2);
        Map<Long, String> fileTypeByBookId = new HashMap<>(bookCount * 2);

        Instant now = Instant.now();
        int seriesCount = Math.max(1, bookCount / 10); // half of all books in series of 5

        try (BatchWriter books = new BatchWriter(db,
                     "INSERT INTO book (id, library_id, library_path_id, is_physical, added_on, scanned_on, deleted)"
                             + " VALUES (?, ?, ?, 0, ?, ?, 0)");
             BatchWriter metadata = new BatchWriter(db,
                     "INSERT INTO book_metadata (book_id, title, publisher, published_date, description, series_name,"
                             + " series_number, series_total, isbn_13, page_count, language, rating, review_count, search_text)"
                             + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             BatchWriter files = new BatchWriter(db,
                     "INSERT INTO book_file (id, book_id, file_name, file_sub_path, is_book, is_folder_based, book_type,"
                             + " is_fixed_layout, file_size_kb, initial_hash, current_hash, added_on)"
                             + " VALUES (?, ?, ?, '', 1, ?, ?, 0, ?, ?, ?, ?)");
             BatchWriter authorMapping = new BatchWriter(db,
                     "INSERT INTO book_metadata_author_mapping (book_id, author_id, sort_order) VALUES (?, ?, ?)");
             BatchWriter categoryMapping = new BatchWriter(db,
                     "INSERT INTO book_metadata_category_mapping (book_id, category_id) VALUES (?, ?)");
             BatchWriter tagMapping = new BatchWriter(db,
                     "INSERT INTO book_metadata_tag_mapping (book_id, tag_id) VALUES (?, ?)")) {

            for (int i = 0; i < bookCount; i++) {
                long bookId = nextBookId + i;
                long fileId = nextFileId + i;
                int libraryIdx = i % libraryIds.size();

                Instant addedOn = now.minus(RANDOM.nextInt(365), ChronoUnit.DAYS)
                        .minus(RANDOM.nextInt(86_400), ChronoUnit.SECONDS);

                books.ps().setLong(1, bookId);
                books.ps().setLong(2, libraryIds.get(libraryIdx));
                books.ps().setLong(3, libraryPathIds.get(libraryIdx));
                books.ps().setTimestamp(4, Timestamp.from(addedOn));
                books.ps().setTimestamp(5, Timestamp.from(addedOn));
                books.add();

                String title = randomTitle();
                boolean inSeries = i < seriesCount * 5L;
                String seriesName = inSeries ? "Perf-Series-" + String.format("%04d", (i / 5) + 1) : null;
                List<Long> bookAuthorIds = pickDistinct(authorIds, RANDOM.nextDouble() < 0.8 ? 1 : 2);

                metadata.ps().setLong(1, bookId);
                metadata.ps().setString(2, title);
                metadata.ps().setString(3, PUBLISHERS[RANDOM.nextInt(PUBLISHERS.length)]);
                metadata.ps().setDate(4, java.sql.Date.valueOf(
                        LocalDate.of(1970 + RANDOM.nextInt(56), 1 + RANDOM.nextInt(12), 1 + RANDOM.nextInt(28))));
                if (RANDOM.nextDouble() < 0.5) {
                    metadata.ps().setString(5, "Performance test description for " + title + ". "
                            + "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt.");
                } else {
                    metadata.ps().setNull(5, Types.VARCHAR);
                }
                if (seriesName != null) {
                    metadata.ps().setString(6, seriesName);
                    metadata.ps().setFloat(7, (i % 5) + 1);
                    metadata.ps().setInt(8, 5);
                } else {
                    metadata.ps().setNull(6, Types.VARCHAR);
                    metadata.ps().setNull(7, Types.FLOAT);
                    metadata.ps().setNull(8, Types.INTEGER);
                }
                metadata.ps().setString(9, "978" + String.format("%010d", RANDOM.nextLong(0, 10_000_000_000L)));
                metadata.ps().setInt(10, 50 + RANDOM.nextInt(1_450));
                metadata.ps().setString(11, randomLanguage());
                if (RANDOM.nextDouble() < 0.7) {
                    metadata.ps().setDouble(12, Math.round((2.5 + RANDOM.nextDouble() * 2.5) * 10.0) / 10.0);
                    metadata.ps().setInt(13, RANDOM.nextInt(5_000));
                } else {
                    metadata.ps().setNull(12, Types.DOUBLE);
                    metadata.ps().setNull(13, Types.INTEGER);
                }
                metadata.ps().setString(14, buildSearchText(title, seriesName, bookAuthorIds, authorIds));
                metadata.add();

                String fileType = randomFileType();
                String extension = switch (fileType) {
                    case "PDF" -> ".pdf";
                    case "CBX" -> ".cbz";
                    case "AUDIOBOOK" -> ".m4b";
                    default -> ".epub";
                };
                boolean folderBased = "AUDIOBOOK".equals(fileType);
                long fileSizeKb = switch (fileType) {
                    case "PDF" -> 1_000 + RANDOM.nextInt(49_000);
                    case "CBX" -> 5_000 + RANDOM.nextInt(95_000);
                    case "AUDIOBOOK" -> 50_000 + RANDOM.nextInt(450_000);
                    default -> 300 + RANDOM.nextInt(4_700);
                };
                String hash = randomHash();
                files.ps().setLong(1, fileId);
                files.ps().setLong(2, bookId);
                files.ps().setString(3, "book-" + bookId + extension);
                files.ps().setBoolean(4, folderBased);
                files.ps().setString(5, fileType);
                files.ps().setLong(6, fileSizeKb);
                files.ps().setString(7, hash);
                files.ps().setString(8, hash);
                files.ps().setTimestamp(9, Timestamp.from(addedOn));
                files.add();

                for (int a = 0; a < bookAuthorIds.size(); a++) {
                    authorMapping.ps().setLong(1, bookId);
                    authorMapping.ps().setLong(2, bookAuthorIds.get(a));
                    authorMapping.ps().setInt(3, a);
                    authorMapping.add();
                }
                for (Long categoryId : pickDistinct(categoryIds, 1 + RANDOM.nextInt(2))) {
                    categoryMapping.ps().setLong(1, bookId);
                    categoryMapping.ps().setLong(2, categoryId);
                    categoryMapping.add();
                }
                for (Long tagId : pickDistinct(tagIds, RANDOM.nextInt(4))) {
                    tagMapping.ps().setLong(1, bookId);
                    tagMapping.ps().setLong(2, tagId);
                    tagMapping.add();
                }

                bookIds.add(bookId);
                fileIdByBookId.put(bookId, fileId);
                fileTypeByBookId.put(bookId, fileType);

                if ((i + 1) % 2_000 == 0) {
                    books.flush();
                    metadata.flush();
                    files.flush();
                    authorMapping.flush();
                    categoryMapping.flush();
                    tagMapping.flush();
                    db.commit();
                    System.out.println("[DataSeeder]   books: " + (i + 1) + "/" + bookCount);
                }
            }
            books.flush();
            metadata.flush();
            files.flush();
            authorMapping.flush();
            categoryMapping.flush();
            tagMapping.flush();
        }
        return new BookIds(bookIds, fileIdByBookId, fileTypeByBookId);
    }

    private static void seedShelfAssignments(Connection db, List<Long> userIds, ShelfIds shelfIds,
                                             List<Long> bookIds) throws SQLException {
        long assigned = 0;
        try (BatchWriter mapping = new BatchWriter(db,
                "INSERT IGNORE INTO book_shelf_mapping (book_id, shelf_id) VALUES (?, ?)")) {
            for (int u = 0; u < userIds.size(); u++) {
                List<Long> userShelfIds = shelfIds.byUserId.get(userIds.get(u));
                for (int s = 0; s < userShelfIds.size(); s++) {
                    int booksOnShelf = s == 0 ? 20 + RANDOM.nextInt(21) : 10 + RANDOM.nextInt(11);
                    for (Long bookId : pickDistinct(bookIds, booksOnShelf)) {
                        mapping.ps().setLong(1, bookId);
                        mapping.ps().setLong(2, userShelfIds.get(s));
                        mapping.add();
                        assigned++;
                    }
                }
                if ((u + 1) % 200 == 0 || u + 1 == userIds.size()) {
                    mapping.flush();
                    System.out.println("[DataSeeder]   shelf assignments: user " + (u + 1) + "/" + userIds.size());
                }
            }
            mapping.flush();
        }
        System.out.println("[DataSeeder] Shelf assignments seeded: " + assigned);
    }

    private static void seedProgress(Connection db, List<Long> userIds, BookIds bookIds) throws SQLException {
        Instant now = Instant.now();
        long rows = 0;
        try (BatchWriter progress = new BatchWriter(db,
                     "INSERT INTO user_book_progress (user_id, book_id, read_status, last_read_time, date_finished,"
                             + " read_status_modified_time, personal_rating) VALUES (?, ?, ?, ?, ?, ?, ?)");
             BatchWriter fileProgress = new BatchWriter(db,
                     "INSERT INTO user_book_file_progress (user_id, book_file_id, progress_percent, last_read_time)"
                             + " VALUES (?, ?, ?, ?)")) {
            for (int u = 0; u < userIds.size(); u++) {
                long userId = userIds.get(u);
                for (Long bookId : pickDistinct(bookIds.bookIds, TestConfig.PROGRESS_BOOKS_PER_USER)) {
                    double r = RANDOM.nextDouble();
                    String status = r < 0.40 ? "READ" : r < 0.65 ? "READING" : r < 0.85 ? "UNREAD" : r < 0.95 ? "PAUSED" : "PARTIALLY_READ";
                    Timestamp lastRead = null;
                    Timestamp finished = null;
                    Float percent = null;
                    switch (status) {
                        case "READ" -> {
                            finished = Timestamp.from(now.minus(RANDOM.nextInt(700), ChronoUnit.DAYS));
                            lastRead = finished;
                            percent = 100f;
                        }
                        case "READING" -> {
                            lastRead = Timestamp.from(now.minus(RANDOM.nextInt(14), ChronoUnit.DAYS));
                            percent = 5 + RANDOM.nextFloat() * 90;
                        }
                        case "PAUSED", "PARTIALLY_READ" -> {
                            lastRead = Timestamp.from(now.minus(15 + RANDOM.nextInt(120), ChronoUnit.DAYS));
                            percent = 5 + RANDOM.nextFloat() * 90;
                        }
                        default -> {
                            // UNREAD: no timestamps, no file progress
                        }
                    }

                    progress.ps().setLong(1, userId);
                    progress.ps().setLong(2, bookId);
                    progress.ps().setString(3, status);
                    setNullableTimestamp(progress.ps(), 4, lastRead);
                    setNullableTimestamp(progress.ps(), 5, finished);
                    setNullableTimestamp(progress.ps(), 6, lastRead);
                    if ("READ".equals(status) && RANDOM.nextDouble() < 0.6) {
                        progress.ps().setInt(7, 1 + RANDOM.nextInt(5));
                    } else {
                        progress.ps().setNull(7, Types.INTEGER);
                    }
                    progress.add();
                    rows++;

                    if (percent != null && !"READ".equals(status)) {
                        Long fileId = bookIds.fileIdByBookId.get(bookId);
                        if (fileId != null) {
                            fileProgress.ps().setLong(1, userId);
                            fileProgress.ps().setLong(2, fileId);
                            fileProgress.ps().setFloat(3, percent);
                            setNullableTimestamp(fileProgress.ps(), 4, lastRead);
                            fileProgress.add();
                        }
                    }
                }
                if ((u + 1) % 200 == 0 || u + 1 == userIds.size()) {
                    progress.flush();
                    fileProgress.flush();
                    System.out.println("[DataSeeder]   progress: user " + (u + 1) + "/" + userIds.size());
                }
            }
            progress.flush();
            fileProgress.flush();
        }
        System.out.println("[DataSeeder]   progress rows: " + rows);
    }

    private static void seedReadingSessions(Connection db, List<Long> userIds, BookIds bookIds) throws SQLException {
        Instant now = Instant.now();
        long rows = 0;
        try (BatchWriter sessions = new BatchWriter(db,
                "INSERT INTO reading_sessions (user_id, book_id, book_type, start_time, end_time, duration_seconds,"
                        + " duration_formatted, start_progress, end_progress, progress_delta, start_location, end_location, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int u = 0; u < userIds.size(); u++) {
                long userId = userIds.get(u);
                for (int s = 0; s < TestConfig.SESSIONS_PER_USER; s++) {
                    long bookId = bookIds.bookIds.get(RANDOM.nextInt(bookIds.bookIds.size()));
                    String bookType = bookIds.fileTypeByBookId.getOrDefault(bookId, "EPUB");
                    int durationSeconds = 300 + RANDOM.nextInt(6_900);
                    Instant start = now.minus(RANDOM.nextInt(730), ChronoUnit.DAYS)
                            .minus(RANDOM.nextInt(86_400), ChronoUnit.SECONDS);
                    Instant end = start.plus(durationSeconds, ChronoUnit.SECONDS);
                    float startProgress = RANDOM.nextFloat() * 0.9f;
                    float delta = 0.01f + RANDOM.nextFloat() * 0.09f;

                    sessions.ps().setLong(1, userId);
                    sessions.ps().setLong(2, bookId);
                    sessions.ps().setString(3, bookType);
                    sessions.ps().setTimestamp(4, Timestamp.from(start));
                    sessions.ps().setTimestamp(5, Timestamp.from(end));
                    sessions.ps().setInt(6, durationSeconds);
                    sessions.ps().setString(7, (durationSeconds / 3600) + "h " + ((durationSeconds % 3600) / 60) + "m");
                    sessions.ps().setFloat(8, startProgress);
                    sessions.ps().setFloat(9, Math.min(1.0f, startProgress + delta));
                    sessions.ps().setFloat(10, delta);
                    sessions.ps().setString(11, "loc-" + RANDOM.nextInt(1_000));
                    sessions.ps().setString(12, "loc-" + (1_000 + RANDOM.nextInt(1_000)));
                    sessions.ps().setTimestamp(13, Timestamp.from(end));
                    sessions.add();
                    rows++;
                }
                if ((u + 1) % 100 == 0 || u + 1 == userIds.size()) {
                    sessions.flush();
                    db.commit();
                    System.out.println("[DataSeeder]   reading sessions: " + rows + " rows so far (user "
                            + (u + 1) + "/" + userIds.size() + ")");
                }
            }
            sessions.flush();
        }
    }

    private static void writeManifest(List<Long> libraryIds, BookIds seededBooks, ShelfIds shelfIds, List<Long> authorIds) {
        List<Long> bookIds = seededBooks.bookIds;
        long authorBase = authorIds.get(0);
        List<String> authorNames = authorIds.stream()
                .map(id -> "Perf Author " + String.format("%04d", (int) (id - authorBase + 1)))
                .toList();

        List<String> searchTerms = new ArrayList<>();
        for (String noun : NOUNS) {
            searchTerms.add(noun.toLowerCase());
        }

        List<String> seriesNames = new ArrayList<>();
        int seriesCount = Math.max(1, bookIds.size() / 10);
        Set<String> seen = new HashSet<>();
        while (seriesNames.size() < Math.min(200, seriesCount)) {
            String name = "Perf-Series-" + String.format("%04d", 1 + RANDOM.nextInt(seriesCount));
            if (seen.add(name)) {
                seriesNames.add(name);
            }
        }

        SeedManifest.create()
                .setLongs(SeedManifest.LIBRARY_IDS, libraryIds)
                .setLong(SeedManifest.BOOK_ID_MIN, bookIds.get(0))
                .setLong(SeedManifest.BOOK_ID_MAX, bookIds.get(bookIds.size() - 1))
                .setLongs(SeedManifest.FAVORITES_SHELF_IDS, shelfIds.favoritesShelfIds)
                .setLongs(SeedManifest.SHELF_IDS, sample(shelfIds.allShelfIds, 200))
                .setLongs(SeedManifest.AUTHOR_IDS, sample(authorIds, 200))
                .setStrings(SeedManifest.AUTHOR_NAMES, sample(authorNames, 200))
                .setStrings(SeedManifest.SERIES_NAMES, seriesNames)
                .setStrings(SeedManifest.LANGUAGES, List.of(LANGUAGES))
                .setStrings(SeedManifest.SEARCH_TERMS, searchTerms)
                .setLong(SeedManifest.USER_COUNT, TestConfig.USER_COUNT)
                .setLong(SeedManifest.BOOK_COUNT, TestConfig.BOOK_COUNT)
                .store();
    }

    private static void validateLogin() {
        System.out.println("[DataSeeder] Validating seeded login via API (" + TestConfig.username(1) + ")...");
        String token = AuthSupport.token(TestConfig.username(1));
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Login validation failed: empty access token");
        }
        System.out.println("[DataSeeder] Login OK — BCrypt hash and API auth verified.");
    }

    // ------------------------------------------------------------------
    // Random data helpers
    // ------------------------------------------------------------------

    private static String randomTitle() {
        String adjective = ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[RANDOM.nextInt(NOUNS.length)];
        String pattern = TITLE_PATTERNS[RANDOM.nextInt(TITLE_PATTERNS.length)];
        return pattern.formatted(adjective, noun) + " " + (1_000 + RANDOM.nextInt(9_000));
    }

    private static String randomLanguage() {
        double r = RANDOM.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < LANGUAGES.length; i++) {
            cumulative += LANGUAGE_WEIGHTS[i];
            if (r < cumulative) {
                return LANGUAGES[i];
            }
        }
        return "en";
    }

    private static String randomFileType() {
        double r = RANDOM.nextDouble();
        return r < 0.60 ? "EPUB" : r < 0.85 ? "PDF" : r < 0.95 ? "CBX" : "AUDIOBOOK";
    }

    private static String randomHash() {
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%016x", RANDOM.nextLong()));
        }
        return sb.toString();
    }

    /** Mimics BookUtils.buildSearchText (lowercased; generated names are ASCII so no diacritics handling needed). */
    private static String buildSearchText(String title, String seriesName, List<Long> bookAuthorIds, List<Long> allAuthorIds) {
        StringBuilder sb = new StringBuilder(title);
        if (seriesName != null) {
            sb.append(' ').append(seriesName);
        }
        long authorBase = allAuthorIds.get(0);
        for (Long authorId : bookAuthorIds) {
            sb.append(' ').append("Perf Author ").append(String.format("%04d", (int) (authorId - authorBase + 1)));
        }
        return sb.toString().toLowerCase();
    }

    private static List<Long> pickDistinct(List<Long> source, int count) {
        if (count <= 0 || source.isEmpty()) {
            return List.of();
        }
        if (count >= source.size()) {
            return new ArrayList<>(source);
        }
        Set<Long> picked = new HashSet<>(count * 2);
        while (picked.size() < count) {
            picked.add(source.get(RANDOM.nextInt(source.size())));
        }
        return new ArrayList<>(picked);
    }

    private static <T> List<T> sample(List<T> source, int max) {
        return source.size() <= max ? source : source.subList(0, max);
    }

    private static void setNullableTimestamp(PreparedStatement ps, int index, Timestamp value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, value);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    // ------------------------------------------------------------------
    // Batch writer
    // ------------------------------------------------------------------

    /**
     * Accumulates batched inserts. Batches are executed ONLY on explicit {@link #flush()} —
     * callers flush dependent writers in FK-safe (parent-before-child) order, otherwise a
     * child batch could hit the database before its parent rows exist.
     */
    private static final class BatchWriter implements AutoCloseable {
        private final PreparedStatement ps;
        private int pending;

        private BatchWriter(Connection connection, String sql) throws SQLException {
            this.ps = connection.prepareStatement(sql);
        }

        PreparedStatement ps() {
            return ps;
        }

        void add() throws SQLException {
            ps.addBatch();
            pending++;
        }

        void flush() throws SQLException {
            if (pending > 0) {
                ps.executeBatch();
                pending = 0;
            }
        }

        @Override
        public void close() throws SQLException {
            ps.close();
        }
    }

    private DataSeeder() {
    }
}
