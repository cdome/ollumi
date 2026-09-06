package org.booklore.perf.seed;

import org.booklore.perf.config.TestConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Removes all perf test data. Deletion order respects FK cascades:
 * deleting perf users cascades permissions/shelves/progress/sessions,
 * deleting perf libraries cascades library paths → books → metadata/mappings/files.
 * Dictionary rows (author/category/tag) are then orphaned and deleted by name prefix.
 */
public final class DataCleaner {

    public static void main(String[] args) throws SQLException {
        System.out.println("[DataCleaner] Target DB: " + TestConfig.DB_URL);
        try (Connection db = DriverManager.getConnection(TestConfig.DB_URL, TestConfig.DB_USER, TestConfig.DB_PASSWORD);
             Statement st = db.createStatement()) {

            int users = st.executeUpdate("DELETE FROM users WHERE username LIKE 'perf-%'");
            System.out.println("[DataCleaner] Deleted " + users + " perf users (cascades: permissions, shelves,"
                    + " shelf mappings, progress, sessions, library mappings)");

            int libraries = st.executeUpdate("DELETE FROM library WHERE name LIKE '" + TestConfig.LIBRARY_NAME_PREFIX + "%'");
            System.out.println("[DataCleaner] Deleted " + libraries + " perf libraries (cascades: paths, books, metadata,"
                    + " files, mappings, progress)");

            int authors = st.executeUpdate("DELETE FROM author WHERE name LIKE 'Perf Author %'");
            int categories = st.executeUpdate("DELETE FROM category WHERE name LIKE 'Perf Category %'");
            int tags = st.executeUpdate("DELETE FROM tag WHERE name LIKE 'Perf Tag %'");
            System.out.println("[DataCleaner] Deleted dictionaries: " + authors + " authors, "
                    + categories + " categories, " + tags + " tags");
        }
        SeedManifest.delete();
        System.out.println("[DataCleaner] DONE. Seed manifest removed.");
    }

    private DataCleaner() {
    }
}
