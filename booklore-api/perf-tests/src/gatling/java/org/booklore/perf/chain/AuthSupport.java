package org.booklore.perf.chain;

import org.booklore.perf.config.AppHttp;
import org.booklore.perf.config.TestConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches JWT access tokens for perf users.
 *
 * Login is rate-limited by the app (per IP and username), so tokens are
 * pre-fetched with pacing in the simulation's {@code before()} hook instead of
 * hammering {@code POST /api/v1/auth/login} from the load profile itself.
 * Access tokens live 10h, far longer than any test run, so no refresh handling.
 */
public final class AuthSupport {

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    /** Pause between logins during pre-fetch to stay clear of the login rate limiter. */
    private static final long LOGIN_PACE_MS = 150;
    private static final int MAX_LOGIN_ATTEMPTS = 8;
    private static final long RETRY_BACKOFF_MS = 2_000;

    private AuthSupport() {
    }

    /** Pre-fetches tokens for perf-user-0001..perf-user-{userCount}, paced and with retries. */
    public static void prefetchTokens(int userCount) {
        System.out.println("[AuthSupport] Pre-fetching tokens for " + userCount + " users at "
                + TestConfig.BASE_URL + " ...");
        long startedAt = System.currentTimeMillis();
        for (int i = 1; i <= userCount; i++) {
            token(TestConfig.username(i));
            if (i % 25 == 0 || i == userCount) {
                System.out.println("[AuthSupport]   " + i + "/" + userCount + " tokens acquired");
            }
            sleep(LOGIN_PACE_MS);
        }
        System.out.println("[AuthSupport] All tokens acquired in " + (System.currentTimeMillis() - startedAt) + " ms");
    }

    /** Returns the cached access token, logging in on first use. */
    public static String token(String username) {
        return TOKENS.computeIfAbsent(username, AuthSupport::loginWithRetry);
    }

    public static String bearer(String username) {
        return "Bearer " + token(username);
    }

    private static String loginWithRetry(String username) {
        String body = "{\"username\":\"" + AppHttp.jsonEscape(username)
                + "\",\"password\":\"" + AppHttp.jsonEscape(TestConfig.PERF_USER_PASSWORD) + "\"}";
        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            AppHttp.Response response = AppHttp.postJson("/api/v1/auth/login", body);
            if (response.is2xx()) {
                return AppHttp.extractJsonString(response.body(), "accessToken");
            }
            if (response.status() == 429 || response.status() >= 500) {
                System.out.println("[AuthSupport] Login for " + username + " got HTTP " + response.status()
                        + " — retry " + attempt + "/" + MAX_LOGIN_ATTEMPTS + " after backoff");
                sleep(RETRY_BACKOFF_MS * attempt);
                continue;
            }
            throw new IllegalStateException("Login failed for " + username + " at " + TestConfig.BASE_URL
                    + ": HTTP " + response.status() + " — " + response.body()
                    + " (did you run './gradlew seedData' against this environment?)");
        }
        throw new IllegalStateException("Login failed for " + username + " after " + MAX_LOGIN_ATTEMPTS
                + " attempts (rate limited?). Reduce perf.concurrentUsers or increase pre-fetch pacing.");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pacing logins", e);
        }
    }
}
