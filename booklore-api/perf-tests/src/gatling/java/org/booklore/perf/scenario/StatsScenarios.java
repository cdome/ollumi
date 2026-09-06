package org.booklore.perf.scenario;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import org.booklore.perf.feed.Feeders;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * User reading/listening statistics — heavy per-user aggregation queries over
 * the reading_sessions table (seeded with ~2 years of history per user).
 */
public final class StatsScenarios {

    public static final String REQ_HEATMAP = "Stats / GET /api/v1/user-stats/reading/heatmap";
    public static final String REQ_STREAK = "Stats / GET /api/v1/user-stats/reading/streak";
    public static final String REQ_GENRES = "Stats / GET /api/v1/user-stats/reading/genres";
    public static final String REQ_SPEED = "Stats / GET /api/v1/user-stats/reading/speed";
    public static final String REQ_TIMELINE = "Stats / GET /api/v1/user-stats/reading/timeline";
    public static final String REQ_FAVORITE_DAYS = "Stats / GET /api/v1/user-stats/reading/favorite-days";
    public static final String REQ_LISTENING_TREND = "Stats / GET /api/v1/user-stats/listening/weekly-trend";
    public static final String REQ_LISTENING_COMPLETION = "Stats / GET /api/v1/user-stats/listening/completion";

    private StatsScenarios() {
    }

    private static ChainBuilder authed() {
        return exec(session -> session.set("auth", org.booklore.perf.chain.AuthSupport.bearer(session.getString("username"))));
    }

    public static ScenarioBuilder stats() {
        return scenario("Reading Stats")
                .feed(Feeders.users())
                .exec(authed())
                .forever().on(
                        feed(Feeders.statPeriod())
                                .exec(http(REQ_HEATMAP)
                                        .get("/api/v1/user-stats/reading/heatmap")
                                        .queryParam("year", "#{statYear}")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_STREAK)
                                        .get("/api/v1/user-stats/reading/streak")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_GENRES)
                                        .get("/api/v1/user-stats/reading/genres")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_SPEED)
                                        .get("/api/v1/user-stats/reading/speed")
                                        .queryParam("year", "#{statYear}")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_TIMELINE)
                                        .get("/api/v1/user-stats/reading/timeline")
                                        .queryParam("year", "#{statYear}")
                                        .queryParam("week", "#{statWeek}")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_FAVORITE_DAYS)
                                        .get("/api/v1/user-stats/reading/favorite-days")
                                        .queryParam("year", "#{statYear}")
                                        .queryParam("month", "#{statMonth}")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_LISTENING_TREND)
                                        .get("/api/v1/user-stats/listening/weekly-trend")
                                        .queryParam("weeks", 26)
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(1, 2)
                                .exec(http(REQ_LISTENING_COMPLETION)
                                        .get("/api/v1/user-stats/listening/completion")
                                        .header("Authorization", "#{auth}")
                                        .check(status().is(200)))
                                .pause(2, 4)
                );
    }
}
