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

public class KoreaderUserIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanUpsertAndGetCurrentKoreaderUser() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, String> request = Map.of(
                "username", "koreader-admin-" + UUID.randomUUID(),
                "password", "koreader-password"
        );

        ResponseEntity<Map> upsertResponse = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(upsertResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upsertResponse.getBody().get("username")).isEqualTo(request.get("username"));
        assertThat(upsertResponse.getBody()).containsKey("passwordMD5");

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("username")).isEqualTo(request.get("username"));
    }

    @Test
    void adminCanToggleSyncFlags() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, String> request = Map.of(
                "username", "koreader-toggle-" + UUID.randomUUID(),
                "password", "koreader-password"
        );

        rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        ResponseEntity<Void> syncResponse = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me/sync?enabled=true",
                HttpMethod.PATCH,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(syncResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> progressResponse = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me/sync-progress-with-booklore?enabled=true",
                HttpMethod.PATCH,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(progressResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("syncEnabled")).isEqualTo(true);
        assertThat(getResponse.getBody().get("syncWithBookloreReader")).isEqualTo(true);
    }

    @Test
    void userWithKoReaderSyncPermissionCanUpsertAndGet() {
        BookLoreUserEntity user = auth.createUser("koreader-perm-" + UUID.randomUUID(), "password",
                perms -> perms.setPermissionSyncKoreader(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Map<String, String> request = Map.of(
                "username", "koreader-own-" + UUID.randomUUID(),
                "password", "koreader-password"
        );

        ResponseEntity<Map> upsertResponse = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(upsertResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void regularUserCannotGetKoreaderUser() {
        BookLoreUserEntity user = auth.createUser("koreader-get-denied-" + UUID.randomUUID(), "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void regularUserCannotUpsertKoreaderUser() {
        BookLoreUserEntity user = auth.createUser("koreader-upsert-denied-" + UUID.randomUUID(), "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Map<String, String> request = Map.of(
                "username", "koreader-denied-" + UUID.randomUUID(),
                "password", "koreader-password"
        );

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void regularUserCannotToggleSync() {
        BookLoreUserEntity user = auth.createUser("koreader-toggle-denied-" + UUID.randomUUID(), "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me/sync?enabled=true",
                HttpMethod.PATCH,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
