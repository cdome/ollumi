package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookLoreUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class OpdsUserIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanListOpdsUsers() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v2/opds-users",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void adminCanCreateOpdsUser() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        String username = "opds-admin-" + UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "username", username,
                "password", "opds-password",
                "sortOrder", "RECENT"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v2/opds-users",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("username")).isEqualTo(username);
        assertThat(response.getBody()).containsKey("id");
    }

    @Test
    void userWithOpdsPermissionCanCreateAndListOwnUser() {
        BookLoreUserEntity user = auth.createUser("opds-perm-" + UUID.randomUUID(), "password",
                perms -> perms.setPermissionAccessOpds(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        String username = "opds-own-" + UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "username", username,
                "password", "opds-password",
                "sortOrder", "TITLE_ASC"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v2/opds-users",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v2/opds-users",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .extracting(u -> u.get("username"))
                .contains(username);
    }

    @Test
    void userWithOpdsPermissionCanUpdateOwnOpdsUser() {
        BookLoreUserEntity user = auth.createUser("opds-update-" + UUID.randomUUID(), "password",
                perms -> perms.setPermissionAccessOpds(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        String username = "opds-update-target-" + UUID.randomUUID();

        Map<String, Object> createRequest = Map.of(
                "username", username,
                "password", "opds-password",
                "sortOrder", "RECENT"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v2/opds-users",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        Integer id = (Integer) createResponse.getBody().get("id");

        Map<String, Object> updateRequest = Map.of("sortOrder", "TITLE_DESC");
        ResponseEntity<Map> updateResponse = rest.exchange(
                baseUrl() + "/api/v2/opds-users/" + id,
                HttpMethod.PATCH,
                auth.bearerEntity(updateRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("sortOrder")).isEqualTo("TITLE_DESC");
    }

    @Test
    void userWithOpdsPermissionCanDeleteOwnOpdsUser() {
        BookLoreUserEntity user = auth.createUser("opds-delete-" + UUID.randomUUID(), "password",
                perms -> perms.setPermissionAccessOpds(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        String username = "opds-delete-target-" + UUID.randomUUID();

        Map<String, Object> createRequest = Map.of(
                "username", username,
                "password", "opds-password",
                "sortOrder", "RECENT"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v2/opds-users",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        Integer id = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v2/opds-users/" + id,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void regularUserCannotAccessOpdsUsers() {
        BookLoreUserEntity user = auth.createUser("opds-regular-" + UUID.randomUUID(), "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> listResponse = rest.exchange(
                baseUrl() + "/api/v2/opds-users",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> request = Map.of(
                "username", "opds-denied-" + UUID.randomUUID(),
                "password", "opds-password",
                "sortOrder", "RECENT"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v2/opds-users",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
