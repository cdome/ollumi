package org.booklore.perf.scenario;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import org.booklore.perf.config.PerfContext;
import org.booklore.perf.config.TestConfig;
import org.booklore.perf.feed.Feeders;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Shelf browsing: web shelf endpoints, app shelf summary, and magic shelves.
 * Shelf reads are scoped to the session user's own favorites shelf because
 * shelf access is ownership-checked server-side.
 */
public final class ShelfScenarios {

    public static final String REQ_SHELVES = "Shelves / GET /api/v1/shelves";
    public static final String REQ_SHELF_BOOKS = "Shelves / GET /api/v1/shelves/{id}/books";
    public static final String REQ_APP_SHELVES = "Shelves / GET /api/v1/app/shelves";
    public static final String REQ_MAGIC_SHELVES = "Shelves / GET /api/magic-shelves";
    public static final String REQ_SERIES = "SeriesAuthors / GET /api/v1/app/series";
    public static final String REQ_SERIES_BOOKS = "SeriesAuthors / GET /api/v1/app/series/{name}/books";
    public static final String REQ_AUTHORS = "SeriesAuthors / GET /api/v1/app/authors";
    public static final String REQ_AUTHOR_DETAIL = "SeriesAuthors / GET /api/v1/app/authors/{id}";

    private ShelfScenarios() {
    }

    private static ChainBuilder authed() {
        return exec(session -> session.set("auth", org.booklore.perf.chain.AuthSupport.bearer(session.getString("username"))));
    }

    private static ChainBuilder ownFavoritesShelf() {
        return exec(session -> {
            String username = session.getString("username");
            int userIndex = Integer.parseInt(username.substring(TestConfig.USERNAME_PREFIX.length()));
            return session.set("shelfId", PerfContext.favoritesShelfId(userIndex));
        });
    }

    public static ScenarioBuilder shelves() {
        return scenario("Shelves")
                .feed(Feeders.users())
                .exec(authed())
                .forever().on(
                        exec(http(REQ_SHELVES)
                                        .get("/api/v1/shelves")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(ownFavoritesShelf())
                                .exec(http(REQ_SHELF_BOOKS)
                                        .get("/api/v1/shelves/#{shelfId}/books")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_APP_SHELVES)
                                        .get("/api/v1/app/shelves")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_MAGIC_SHELVES)
                                        .get("/api/magic-shelves")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(2, 4)
                );
    }

    public static ScenarioBuilder seriesAuthors() {
        return scenario("Series & Authors")
                .feed(Feeders.users())
                .exec(authed())
                .forever().on(
                        feed(Feeders.page())
                                .exec(http(REQ_SERIES)
                                        .get("/api/v1/app/series")
                                        .queryParam("page", "#{page}")
                                        .queryParam("size", 20)
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .feed(Feeders.seriesName())
                                .exec(http(REQ_SERIES_BOOKS)
                                        .get("/api/v1/app/series/#{seriesName}/books")
                                        .queryParam("page", 0)
                                        .queryParam("size", 20)
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_AUTHORS)
                                        .get("/api/v1/app/authors")
                                        .queryParam("page", "#{page}")
                                        .queryParam("size", 30)
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .feed(Feeders.authorId())
                                .exec(http(REQ_AUTHOR_DETAIL)
                                        .get("/api/v1/app/authors/#{authorId}")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(2, 4)
                );
    }
}
