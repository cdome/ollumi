package org.booklore.perf.scenario;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import org.booklore.perf.config.PerfContext;
import org.booklore.perf.config.TestConfig;
import org.booklore.perf.feed.Feeders;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.percent;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Filtering and search paths on the mobile app API:
 * GET /api/v1/app/books with various filter combinations, full-text search,
 * and the filter-options endpoint that drives the app's filter UI.
 */
public final class FilterSearchScenarios {

    public static final String REQ_FILTER_OPTIONS = "Filter / GET /api/v1/app/filter-options";
    public static final String REQ_FILTERED = "Filter / GET /api/v1/app/books (filtered)";
    public static final String REQ_SEARCH = "Filter / GET /api/v1/app/books/search";

    private FilterSearchScenarios() {
    }

    private static ChainBuilder authed() {
        return exec(session -> session.set("auth", org.booklore.perf.chain.AuthSupport.bearer(session.getString("username"))));
    }

    /** Resolves the session user's own favorites shelf into "shelfId" (shelves are per-user). */
    private static ChainBuilder ownFavoritesShelf() {
        return exec(session -> {
            String username = session.getString("username");
            int userIndex = Integer.parseInt(username.substring(TestConfig.USERNAME_PREFIX.length()));
            return session.set("shelfId", PerfContext.favoritesShelfId(userIndex));
        });
    }

    private static ChainBuilder filteredBooks() {
        return exec(http(REQ_FILTERED)
                .get("/api/v1/app/books")
                .queryParam("page", 0)
                .queryParam("size", 50)
                .header("Authorization", "#{auth}")
                .check(status().is(200)));
    }

    public static ScenarioBuilder filterSearch() {
        return scenario("Filter & Search")
                .feed(Feeders.users())
                .exec(authed())
                .forever().on(
                        exec(http(REQ_FILTER_OPTIONS)
                                        .get("/api/v1/app/filter-options")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .randomSwitch().on(
                                        percent(25).then(
                                                feed(Feeders.libraryId())
                                                        .exec(http(REQ_FILTERED)
                                                                .get("/api/v1/app/books")
                                                                .queryParam("page", 0)
                                                                .queryParam("size", 50)
                                                                .queryParam("libraryId", "#{libraryId}")
                                                                .header("Authorization", "#{auth}")
                                                                .check(status().is(200)))),
                                        percent(20).then(
                                                ownFavoritesShelf()
                                                        .exec(http(REQ_FILTERED)
                                                                .get("/api/v1/app/books")
                                                                .queryParam("page", 0)
                                                                .queryParam("size", 50)
                                                                .queryParam("shelfId", "#{shelfId}")
                                                                .header("Authorization", "#{auth}")
                                                                .check(status().is(200)))),
                                        percent(20).then(
                                                feed(Feeders.readStatus())
                                                        .exec(http(REQ_FILTERED)
                                                                .get("/api/v1/app/books")
                                                                .queryParam("page", 0)
                                                                .queryParam("size", 50)
                                                                .queryParam("status", "#{readStatus}")
                                                                .header("Authorization", "#{auth}")
                                                                .check(status().is(200)))),
                                        percent(15).then(
                                                feed(Feeders.fileType())
                                                        .exec(http(REQ_FILTERED)
                                                                .get("/api/v1/app/books")
                                                                .queryParam("page", 0)
                                                                .queryParam("size", 50)
                                                                .queryParam("fileType", "#{fileType}")
                                                                .header("Authorization", "#{auth}")
                                                                .check(status().is(200)))),
                                        percent(10).then(
                                                feed(Feeders.authorName())
                                                        .exec(http(REQ_FILTERED)
                                                                .get("/api/v1/app/books")
                                                                .queryParam("page", 0)
                                                                .queryParam("size", 50)
                                                                .queryParam("authors", "#{authorName}")
                                                                .header("Authorization", "#{auth}")
                                                                .check(status().is(200)))),
                                        percent(10).then(
                                                feed(Feeders.language())
                                                        .exec(http(REQ_FILTERED)
                                                                .get("/api/v1/app/books")
                                                                .queryParam("page", 0)
                                                                .queryParam("size", 50)
                                                                .queryParam("language", "#{language}")
                                                                .header("Authorization", "#{auth}")
                                                                .check(status().is(200))))
                                )
                                .pause(1, 3)
                                .feed(Feeders.searchTerm())
                                .exec(http(REQ_SEARCH)
                                        .get("/api/v1/app/books/search")
                                        .queryParam("q", "#{searchTerm}")
                                        .queryParam("page", 0)
                                        .queryParam("size", 20)
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(2, 4)
                );
    }
}
