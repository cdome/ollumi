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

public class MagicShelfIntegrationTest extends RestApiIntegrationTest {

    @Test
    void userCanCreateAndListMagicShelves() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> request = Map.of(
                "name", "Magic Shelf " + UUID.randomUUID(),
                "filterJson", "{\"genre\":\"fantasy\"}",
                "isPublic", false
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/magic-shelves",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).containsKey("id");
        assertThat(createResponse.getBody().get("name")).isEqualTo(request.get("name"));
        assertThat(createResponse.getBody().get("filterJson")).isEqualTo(request.get("filterJson"));

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/magic-shelves",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).anyMatch(shelf -> request.get("name").equals(shelf.get("name")));
    }

    @Test
    void userCanGetMagicShelfById() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookLoreUserEntity admin = auth.userRepository().findByUsername(ADMIN_USERNAME).orElseThrow();

        Map<String, Object> createRequest = Map.of(
                "name", "Magic Shelf By Id " + UUID.randomUUID(),
                "filterJson", "{\"genre\":\"scifi\"}",
                "isPublic", false
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/magic-shelves",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer shelfId = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/magic-shelves/" + shelfId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("id")).isEqualTo(shelfId);
        assertThat(getResponse.getBody().get("name")).isEqualTo(createRequest.get("name"));
    }

    @Test
    void userCanUpdateMagicShelf() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> createRequest = Map.of(
                "name", "Magic Shelf Update " + UUID.randomUUID(),
                "filterJson", "{\"genre\":\"horror\"}",
                "isPublic", false
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/magic-shelves",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer shelfId = (Integer) createResponse.getBody().get("id");

        Map<String, Object> updateRequest = Map.of(
                "id", shelfId.longValue(),
                "name", "Updated Magic Shelf",
                "filterJson", "{\"author\":\"Tolkein\"}",
                "isPublic", false
        );

        ResponseEntity<Map> updateResponse = rest.postForEntity(
                baseUrl() + "/api/magic-shelves",
                auth.bearerEntity(updateRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("id")).isEqualTo(shelfId);
        assertThat(updateResponse.getBody().get("name")).isEqualTo("Updated Magic Shelf");
        assertThat(updateResponse.getBody().get("filterJson")).isEqualTo("{\"author\":\"Tolkein\"}");
    }

    @Test
    void userCanDeleteMagicShelf() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> createRequest = Map.of(
                "name", "Magic Shelf Delete " + UUID.randomUUID(),
                "filterJson", "{\"genre\":\"mystery\"}",
                "isPublic", false
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/magic-shelves",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer shelfId = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/magic-shelves/" + shelfId,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void magicShelfRequiresFilterJson() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> request = Map.of(
                "name", "Invalid Magic Shelf",
                "isPublic", false
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/magic-shelves",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unauthenticatedRequestReturnsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/magic-shelves", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
