package org.booklore.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppBookSortResolverTest {

    private List<Sort.Order> orders(String sortBy, String sortDir) {
        return AppBookSortResolver.resolve(sortBy, sortDir).toList();
    }

    private Sort.Order primary(String sortBy, String sortDir) {
        return orders(sortBy, sortDir).get(0);
    }

    @Test
    void mapsKnownMetadataFieldsToTheirEntityPaths() {
        assertEquals("metadata.title", primary("title", "asc").getProperty());
        assertEquals("metadata.publisher", primary("publisher", "asc").getProperty());
        assertEquals("metadata.publishedDate", primary("publishedDate", "asc").getProperty());
        assertEquals("metadata.seriesNumber", primary("seriesNumber", "asc").getProperty());
        assertEquals("metadata.pageCount", primary("pageCount", "asc").getProperty());
        assertEquals("metadata.rating", primary("rating", "asc").getProperty());
        assertEquals("metadata.amazonRating", primary("amazonRating", "asc").getProperty());
        assertEquals("metadata.narrator", primary("narrator", "asc").getProperty());
        assertEquals("addedOn", primary("addedOn", "asc").getProperty());
    }

    @Test
    void isCaseInsensitiveOnTheFieldName() {
        assertEquals("metadata.title", primary("TITLE", "asc").getProperty());
        assertEquals("metadata.pageCount", primary("PageCount", "asc").getProperty());
    }

    @Test
    void supportsFieldAliases() {
        assertEquals("metadata.seriesName", primary("series", "asc").getProperty());
        assertEquals("metadata.seriesName", primary("seriesName", "asc").getProperty());
        assertEquals("metadata.publishedDate", primary("publishedYear", "asc").getProperty());
    }

    @Test
    void unknownOrNullFieldFallsBackToAddedOn() {
        assertEquals(AppBookSortResolver.DEFAULT_PATH, primary("noSuchField", "asc").getProperty());
        assertEquals(AppBookSortResolver.DEFAULT_PATH, primary(null, "asc").getProperty());
        assertEquals("addedOn", AppBookSortResolver.DEFAULT_PATH);
    }

    @Test
    void ascendingOnlyWhenExplicitlyRequestedOtherwiseDescending() {
        assertEquals(Sort.Direction.ASC, primary("title", "asc").getDirection());
        assertEquals(Sort.Direction.ASC, primary("title", "ASC").getDirection());
        assertEquals(Sort.Direction.DESC, primary("title", "desc").getDirection());
        assertEquals(Sort.Direction.DESC, primary("title", null).getDirection());
        assertEquals(Sort.Direction.DESC, primary("title", "garbage").getDirection());
    }

    @Test
    void alwaysAppendsAnAscendingIdTiebreakerForStablePagination() {
        List<Sort.Order> ordersDesc = orders("title", "desc");
        assertEquals(2, ordersDesc.size());
        Sort.Order tiebreaker = ordersDesc.get(1);
        assertEquals("id", tiebreaker.getProperty());
        assertEquals(Sort.Direction.ASC, tiebreaker.getDirection());

        // present even when the primary field is unknown
        Sort.Order fallbackTiebreaker = orders("noSuchField", "asc").get(1);
        assertNotNull(fallbackTiebreaker);
        assertEquals("id", fallbackTiebreaker.getProperty());
    }
}
