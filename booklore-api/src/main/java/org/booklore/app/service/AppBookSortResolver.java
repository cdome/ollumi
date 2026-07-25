package org.booklore.app.service;

import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Maps a client-supplied sort field name to a stable Spring Data {@link Sort}
 * for the paged books endpoint.
 *
 * <p>Only book- and metadata-level columns are mapped here; user-progress
 * dependent orderings (personalRating, readStatus, lastReadTime, dateFinished,
 * reading progress) require a per-user join and belong to the query layer, so
 * they intentionally fall through to the default ordering for now.
 *
 * <p>Every ordering is given a deterministic {@code id} tiebreaker: without one,
 * rows sharing the primary sort value can reshuffle between page requests, which
 * makes offset pagination drop or duplicate items.
 */
public final class AppBookSortResolver {

    private AppBookSortResolver() {
    }

    static final String DEFAULT_PATH = "addedOn";

    private static final Map<String, String> SORT_FIELD_PATHS = Map.ofEntries(
            Map.entry("title", "metadata.title"),
            Map.entry("publisher", "metadata.publisher"),
            Map.entry("publisheddate", "metadata.publishedDate"),
            Map.entry("publishedyear", "metadata.publishedDate"),
            Map.entry("seriesname", "metadata.seriesName"),
            Map.entry("series", "metadata.seriesName"),
            Map.entry("seriesnumber", "metadata.seriesNumber"),
            Map.entry("pagecount", "metadata.pageCount"),
            Map.entry("rating", "metadata.rating"),
            Map.entry("reviewcount", "metadata.reviewCount"),
            Map.entry("amazonrating", "metadata.amazonRating"),
            Map.entry("goodreadsrating", "metadata.goodreadsRating"),
            Map.entry("hardcoverrating", "metadata.hardcoverRating"),
            Map.entry("narrator", "metadata.narrator"),
            Map.entry("addedon", "addedOn")
    );

    public static Sort resolve(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String key = sortBy != null ? sortBy.toLowerCase() : "";
        String path = SORT_FIELD_PATHS.getOrDefault(key, DEFAULT_PATH);

        return Sort.by(direction, path).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
