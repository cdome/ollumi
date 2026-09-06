package org.booklore.perf.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal blocking HTTP client for bootstrap/auth calls outside of Gatling
 * (seeder health checks, setup wizard, login token pre-fetch).
 * Uses java.net.http — no extra dependencies, no JSON library (flat responses
 * are parsed with small regexes).
 */
public final class AppHttp {

    public record Response(int status, String body) {
        public boolean is2xx() {
            return status >= 200 && status < 300;
        }
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private AppHttp() {
    }

    public static Response get(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TestConfig.BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return send(request);
    }

    public static Response postJson(String path, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TestConfig.BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private static Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new IllegalStateException("HTTP call failed: " + request.method() + " " + request.uri()
                    + " — is the app running at " + TestConfig.BASE_URL + "?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP call interrupted: " + request.method() + " " + request.uri(), e);
        }
    }

    /** Extracts {@code "field":"value"} from a flat JSON object. */
    public static String extractJsonString(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("Field '" + field + "' not found in response body: " + abbreviate(body));
        }
        return matcher.group(1);
    }

    /** Extracts {@code "field":true|false} from a flat JSON object; null when absent. */
    public static Boolean extractJsonBoolean(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(true|false)").matcher(body);
        return matcher.find() ? Boolean.valueOf(matcher.group(1)) : null;
    }

    /** Escapes a value for embedding into a hand-built JSON string. */
    public static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "<null>";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }
}
