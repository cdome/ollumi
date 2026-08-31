package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookLoreUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class KoboSettingsIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanGetKoboSettings() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/kobo-settings",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
        assertThat(response.getBody()).containsKey("syncEnabled");
    }

    @Test
    void adminCanCreateOrUpdateToken() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/kobo-settings/token",
                HttpMethod.PUT,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("token")).isNotNull();
    }

    @Test
    void adminCanUpdateKoboSettings() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> request = Map.of(
                "syncEnabled", true,
                "progressMarkAsReadingThreshold", 0.1,
                "progressMarkAsFinishedThreshold", 0.9,
                "autoAddToShelf", true,
                "twoWayProgressSync", true
        );

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/kobo-settings",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("syncEnabled")).isEqualTo(true);
        assertThat(response.getBody().get("autoAddToShelf")).isEqualTo(true);
        assertThat(response.getBody().get("twoWayProgressSync")).isEqualTo(true);
    }

    @Test
    void userWithKoboSyncPermissionCanCreateOrUpdateToken() {
        BookLoreUserEntity user = auth.createUser("kobo-token-" + UUID.randomUUID(), "password",
                perms -> perms.setPermissionSyncKobo(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/kobo-settings/token",
                HttpMethod.PUT,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("token")).isNotNull();
    }

    @Test
    void userWithKoboSyncPermissionCanUpdateSettings() {
        BookLoreUserEntity user = auth.createUser("kobo-update-" + UUID.randomUUID(), "password",
                perms -> perms.setPermissionSyncKobo(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Map<String, Object> request = Map.of(
                "syncEnabled", false,
                "progressMarkAsReadingThreshold", 0.05,
                "progressMarkAsFinishedThreshold", 0.95,
                "autoAddToShelf", false,
                "twoWayProgressSync", false
        );

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/kobo-settings",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("progressMarkAsReadingThreshold")).isEqualTo(0.05);
    }

    @Test
    void regularUserCannotCreateOrUpdateToken() {
        BookLoreUserEntity user = auth.createUser("kobo-denied-" + UUID.randomUUID(), "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/kobo-settings/token",
                HttpMethod.PUT,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void regularUserCannotUpdateKoboSettings() {
        BookLoreUserEntity user = auth.createUser("kobo-update-denied-" + UUID.randomUUID(), "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Map<String, Object> request = Map.of(
                "syncEnabled", true,
                "progressMarkAsReadingThreshold", 0.1,
                "progressMarkAsFinishedThreshold", 0.9,
                "autoAddToShelf", true,
                "twoWayProgressSync", true
        );

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/kobo-settings",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
