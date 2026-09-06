package org.booklore.perf.scenario;

import org.booklore.perf.feed.Feeders;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Web-UI and mobile-app browsing paths: book listing (unpaginated + paged),
 * book details, libraries, and the app home feeds.
 */
public final class BrowseScenarios {

    public static final String REQ_LIBRARIES = "Web / GET /api/v1/libraries";
    public static final String REQ_BOOKS_ALL = "Web / GET /api/v1/books";
    public static final String REQ_BOOK_DETAIL = "Web / GET /api/v1/books/{id}";
    public static final String REQ_APP_BOOKS_PAGED = "App / GET /api/v1/app/books (paged)";
    public static final String REQ_APP_BOOK_DETAIL = "App / GET /api/v1/app/books/{id}";
    public static final String REQ_CONTINUE_READING = "App / GET /api/v1/app/books/continue-reading";
    public static final String REQ_RECENTLY_ADDED = "App / GET /api/v1/app/books/recently-added";
    public static final String REQ_RANDOM = "App / GET /api/v1/app/books/random";

    private BrowseScenarios() {
    }

    private static ChainBuilder authed() {
        return exec(session -> session.set("auth", org.booklore.perf.chain.AuthSupport.bearer(session.getString("username"))));
    }

    /** Libraries + full unpaginated book dump (the known hotspot) + book detail. */
    public static ScenarioBuilder webBrowse() {
        return scenario("Web Browse")
                .feed(Feeders.users())
                .exec(authed())
                .forever().on(
                        exec(http(REQ_LIBRARIES)
                                        .get("/api/v1/libraries")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_BOOKS_ALL)
                                        .get("/api/v1/books")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(2, 4)
                                .feed(Feeders.bookId())
                                .exec(http(REQ_BOOK_DETAIL)
                                        .get("/api/v1/books/#{bookId}")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 3)
                );
    }

    /** Mobile-style browsing: paged book lists + home feeds. */
    public static ScenarioBuilder appPaging() {
        return scenario("App Paging")
                .feed(Feeders.users())
                .exec(authed())
                .forever().on(
                        feed(Feeders.page())
                                .exec(http(REQ_APP_BOOKS_PAGED)
                                        .get("/api/v1/app/books")
                                        .queryParam("page", "#{page}")
                                        .queryParam("size", 50)
                                        .queryParam("sort", "addedOn")
                                        .queryParam("dir", "desc")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .feed(Feeders.bookId())
                                .exec(http(REQ_APP_BOOK_DETAIL)
                                        .get("/api/v1/app/books/#{bookId}")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_CONTINUE_READING)
                                        .get("/api/v1/app/books/continue-reading")
                                        .queryParam("limit", 10)
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_RECENTLY_ADDED)
                                        .get("/api/v1/app/books/recently-added")
                                        .queryParam("limit", 10)
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_RANDOM)
                                        .get("/api/v1/app/books/random")
                                        .queryParam("page", 0)
                                        .queryParam("size", 20)
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(2, 4)
                );
    }
}
