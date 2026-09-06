package org.booklore.perf.simulation;

import io.gatling.javaapi.http.HttpProtocolBuilder;
import org.booklore.perf.chain.AuthSupport;
import org.booklore.perf.config.PerfContext;
import org.booklore.perf.config.TestConfig;
import org.booklore.perf.scenario.BrowseScenarios;
import org.booklore.perf.scenario.FilterSearchScenarios;
import org.booklore.perf.scenario.ShelfScenarios;
import org.booklore.perf.scenario.StatsScenarios;
import org.booklore.perf.seed.SeedManifest;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * Main load profile: {@code perf.concurrentUsers} (default 100) concurrent users,
 * ramped over {@code perf.rampUp} and held for {@code perf.duration}, split across
 * the six main read-path groups by weight.
 */
public class MixedLoadSimulation extends io.gatling.javaapi.core.Simulation {

    private static final int TOTAL = TestConfig.CONCURRENT_USERS;

    private static final int WEB_USERS = share(25);
    private static final int APP_PAGING_USERS = share(25);
    private static final int FILTER_USERS = share(20);
    private static final int SHELF_USERS = share(10);
    private static final int SERIES_AUTHOR_USERS = share(10);
    private static final int STATS_USERS = Math.max(1,
            TOTAL - WEB_USERS - APP_PAGING_USERS - FILTER_USERS - SHELF_USERS - SERIES_AUTHOR_USERS);

    private static int share(int percent) {
        return Math.max(1, TOTAL * percent / 100);
    }

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(TestConfig.BASE_URL)
            .acceptHeader("application/json")
            .userAgentHeader("booklore-perf-tests/1.0")
            .disableWarmUp();

    /**
     * Fail fast when the dataset was not seeded, then pre-fetch tokens for the
     * whole user pool (paced) so the rate-limited login endpoint stays out of the profile.
     */
    @Override
    public void before() {
        int seededUsers = (int) PerfContext.manifest().getLong(SeedManifest.USER_COUNT);
        AuthSupport.prefetchTokens(Math.min(TOTAL, seededUsers));
    }

    {
        setUp(
                BrowseScenarios.webBrowse().injectClosed(
                        rampConcurrentUsers(0).to(WEB_USERS).during(TestConfig.RAMP_UP),
                        constantConcurrentUsers(WEB_USERS).during(TestConfig.DURATION)),
                BrowseScenarios.appPaging().injectClosed(
                        rampConcurrentUsers(0).to(APP_PAGING_USERS).during(TestConfig.RAMP_UP),
                        constantConcurrentUsers(APP_PAGING_USERS).during(TestConfig.DURATION)),
                FilterSearchScenarios.filterSearch().injectClosed(
                        rampConcurrentUsers(0).to(FILTER_USERS).during(TestConfig.RAMP_UP),
                        constantConcurrentUsers(FILTER_USERS).during(TestConfig.DURATION)),
                ShelfScenarios.shelves().injectClosed(
                        rampConcurrentUsers(0).to(SHELF_USERS).during(TestConfig.RAMP_UP),
                        constantConcurrentUsers(SHELF_USERS).during(TestConfig.DURATION)),
                ShelfScenarios.seriesAuthors().injectClosed(
                        rampConcurrentUsers(0).to(SERIES_AUTHOR_USERS).during(TestConfig.RAMP_UP),
                        constantConcurrentUsers(SERIES_AUTHOR_USERS).during(TestConfig.DURATION)),
                StatsScenarios.stats().injectClosed(
                        rampConcurrentUsers(0).to(STATS_USERS).during(TestConfig.RAMP_UP),
                        constantConcurrentUsers(STATS_USERS).during(TestConfig.DURATION))
        ).protocols(httpProtocol)
                // forever() loops never exit on their own — cap the run (ramp + steady state + drain)
                .maxDuration(TestConfig.RAMP_UP.plus(TestConfig.DURATION).plusSeconds(60))
                .assertions(
                        global().successfulRequests().percent().gt(TestConfig.MIN_SUCCESS_PERCENT),
                        details(BrowseScenarios.REQ_BOOKS_ALL)
                                .responseTime().percentile3().lt(TestConfig.P95_FULL_LIST_MS),
                        details(BrowseScenarios.REQ_APP_BOOKS_PAGED)
                                .responseTime().percentile3().lt(TestConfig.P95_PAGED_MS),
                        details(FilterSearchScenarios.REQ_FILTERED)
                                .responseTime().percentile3().lt(TestConfig.P95_PAGED_MS),
                        details(FilterSearchScenarios.REQ_SEARCH)
                                .responseTime().percentile3().lt(TestConfig.P95_PAGED_MS)
                );
    }
}
