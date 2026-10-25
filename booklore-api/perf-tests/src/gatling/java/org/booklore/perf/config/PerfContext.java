package org.booklore.perf.config;

import org.booklore.perf.seed.SeedManifest;

import java.util.List;

/**
 * Lazily loads and holds the {@link SeedManifest} produced by {@code seedData}.
 * Shared by feeders and scenarios inside the simulation JVM.
 */
public final class PerfContext {

    private static volatile SeedManifest manifest;

    private PerfContext() {
    }

    public static SeedManifest manifest() {
        SeedManifest current = manifest;
        if (current == null) {
            synchronized (PerfContext.class) {
                current = manifest;
                if (current == null) {
                    current = SeedManifest.load();
                    manifest = current;
                }
            }
        }
        return current;
    }

    /** Favorites-shelf id for the given 1-based user index (every seeded user owns one). */
    public static long favoritesShelfId(int userIndex) {
        List<Long> shelfIds = manifest().getLongs(SeedManifest.FAVORITES_SHELF_IDS);
        if (userIndex < 1 || userIndex > shelfIds.size()) {
            throw new IllegalStateException("No favorites shelf for user index " + userIndex
                    + " (manifest has " + shelfIds.size() + ") — re-run './gradlew seedData'.");
        }
        return shelfIds.get(userIndex - 1);
    }
}
