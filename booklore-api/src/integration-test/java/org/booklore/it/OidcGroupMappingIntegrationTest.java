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

public class OidcGroupMappingIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanListMappings() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/admin/oidc-group-mappings",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void adminCanCreateMapping() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        String claim = "claim-" + UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "oidcGroupClaim", claim,
                "isAdmin", false,
                "permissions", List.of("permissionUpload", "permissionDownload"),
                "libraryIds", List.of(),
                "description", "IT mapping"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/admin/oidc-group-mappings",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("oidcGroupClaim")).isEqualTo(claim);
        assertThat(response.getBody().get("isAdmin")).isEqualTo(false);
    }

    @Test
    void adminCanUpdateMapping() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        String claim = "claim-update-" + UUID.randomUUID();

        Map<String, Object> createRequest = Map.of(
                "oidcGroupClaim", claim,
                "isAdmin", false,
                "permissions", List.of(),
                "libraryIds", List.of(),
                "description", "before"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/admin/oidc-group-mappings",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        Integer id = (Integer) createResponse.getBody().get("id");

        Map<String, Object> updateRequest = Map.of(
                "oidcGroupClaim", claim,
                "isAdmin", true,
                "permissions", List.of("permissionAdmin"),
                "libraryIds", List.of(),
                "description", "after"
        );

        ResponseEntity<Map> updateResponse = rest.exchange(
                baseUrl() + "/api/v1/admin/oidc-group-mappings/" + id,
                HttpMethod.PUT,
                auth.bearerEntity(updateRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("isAdmin")).isEqualTo(true);
        assertThat(updateResponse.getBody().get("description")).isEqualTo("after");
    }

    @Test
    void adminCanDeleteMapping() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        String claim = "claim-delete-" + UUID.randomUUID();

        Map<String, Object> createRequest = Map.of(
                "oidcGroupClaim", claim,
                "isAdmin", false,
                "permissions", List.of(),
                "libraryIds", List.of(),
                "description", "to delete"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/admin/oidc-group-mappings",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        Integer id = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/admin/oidc-group-mappings/" + id,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void nonAdminCannotListMappings() {
        BookLoreUserEntity user = auth.createUser("oidc-regular-" + UUID.randomUUID(), "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/admin/oidc-group-mappings",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void nonAdminCannotCreateMapping() {
        BookLoreUserEntity user = auth.createUser("oidc-create-denied-" + UUID.randomUUID(), "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        String claim = "claim-denied-" + UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "oidcGroupClaim", claim,
                "isAdmin", false,
                "permissions", List.of(),
                "libraryIds", List.of(),
                "description", "denied"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/admin/oidc-group-mappings",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
