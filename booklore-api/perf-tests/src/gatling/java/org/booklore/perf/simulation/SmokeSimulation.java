package org.booklore.perf.simulation;

import io.gatling.javaapi.http.HttpProtocolBuilder;
import org.booklore.perf.chain.AuthSupport;
import org.booklore.perf.config.PerfContext;
import org.booklore.perf.config.TestConfig;
import org.booklore.perf.scenario.BrowseScenarios;
import org.booklore.perf.scenario.FilterSearchScenarios;
import org.booklore.perf.scenario.ShelfScenarios;
import org.booklore.perf.scenario.StatsScenarios;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * Quick sanity check: one user per scenario group for ~1 minute.
 * Run this first after seeding to validate the setup before the full load test.
 */
public class SmokeSimulation extends io.gatling.javaapi.core.Simulation {

    private static final Duration RAMP = Duration.ofSeconds(10);
    private static final Duration DURATION = Duration.ofMinutes(1);
    private static final int SCENARIO_GROUPS = 6;

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(TestConfig.BASE_URL)
            .acceptHeader("application/json")
            .userAgentHeader("booklore-perf-tests/1.0 (smoke)")
            .disableWarmUp();

    @Override
    public void before() {
        PerfContext.manifest(); // fail fast with a clear message when seedData was not run
        AuthSupport.prefetchTokens(SCENARIO_GROUPS);
    }

    {
        setUp(
                BrowseScenarios.webBrowse().injectClosed(
                        rampConcurrentUsers(0).to(1).during(RAMP), constantConcurrentUsers(1).during(DURATION)),
                BrowseScenarios.appPaging().injectClosed(
                        rampConcurrentUsers(0).to(1).during(RAMP), constantConcurrentUsers(1).during(DURATION)),
                FilterSearchScenarios.filterSearch().injectClosed(
                        rampConcurrentUsers(0).to(1).during(RAMP), constantConcurrentUsers(1).during(DURATION)),
                ShelfScenarios.shelves().injectClosed(
                        rampConcurrentUsers(0).to(1).during(RAMP), constantConcurrentUsers(1).during(DURATION)),
                ShelfScenarios.seriesAuthors().injectClosed(
                        rampConcurrentUsers(0).to(1).during(RAMP), constantConcurrentUsers(1).during(DURATION)),
                StatsScenarios.stats().injectClosed(
                        rampConcurrentUsers(0).to(1).during(RAMP), constantConcurrentUsers(1).during(DURATION))
        ).protocols(httpProtocol)
                // forever() loops never exit on their own — cap the run
                .maxDuration(RAMP.plus(DURATION).plusSeconds(30))
                .assertions(
                        global().successfulRequests().percent().gt(TestConfig.MIN_SUCCESS_PERCENT)
                );
    }
}
