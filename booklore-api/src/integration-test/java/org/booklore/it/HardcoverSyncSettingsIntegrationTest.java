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

public class HardcoverSyncSettingsIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanGetAndUpdateHardcoverSyncSettings() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/hardcover-sync-settings",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsKey("hardcoverSyncEnabled");

        Map<String, Object> request = Map.of(
                "hardcoverApiKey", "api-key-" + UUID.randomUUID(),
                "hardcoverSyncEnabled", true
        );

        ResponseEntity<Map> putResponse = rest.exchange(
                baseUrl() + "/api/v1/hardcover-sync-settings",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putResponse.getBody().get("hardcoverSyncEnabled")).isEqualTo(true);
        assertThat(putResponse.getBody().get("hardcoverApiKey")).isEqualTo(request.get("hardcoverApiKey"));
    }

    @Test
    void userWithKoReaderSyncPermissionCanGetAndUpdateSettings() {
        BookLoreUserEntity user = auth.createUser("hc-koreader-" + UUID.randomUUID(), "password",
                perms -> perms.setPermissionSyncKoreader(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Map<String, Object> request = Map.of(
                "hardcoverApiKey", "koreader-key-" + UUID.randomUUID(),
                "hardcoverSyncEnabled", true
        );

        ResponseEntity<Map> putResponse = rest.exchange(
                baseUrl() + "/api/v1/hardcover-sync-settings",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/hardcover-sync-settings",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("hardcoverApiKey")).isEqualTo(request.get("hardcoverApiKey"));
    }

    @Test
    void userWithKoboSyncPermissionCanGetAndUpdateSettings() {
        BookLoreUserEntity user = auth.createUser("hc-kobo-" + UUID.randomUUID(), "password",
                perms -> perms.setPermissionSyncKobo(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Map<String, Object> request = Map.of(
                "hardcoverApiKey", "kobo-key-" + UUID.randomUUID(),
                "hardcoverSyncEnabled", false
        );

        ResponseEntity<Map> putResponse = rest.exchange(
                baseUrl() + "/api/v1/hardcover-sync-settings",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/hardcover-sync-settings",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("hardcoverSyncEnabled")).isEqualTo(false);
    }

    @Test
    void regularUserCannotAccessHardcoverSyncSettings() {
        BookLoreUserEntity user = auth.createUser("hc-regular-" + UUID.randomUUID(), "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/hardcover-sync-settings",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> request = Map.of(
                "hardcoverApiKey", "key",
                "hardcoverSyncEnabled", true
        );

        ResponseEntity<Map> putResponse = rest.exchange(
                baseUrl() + "/api/v1/hardcover-sync-settings",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
