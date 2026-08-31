package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ShelfIntegrationTest extends RestApiIntegrationTest {

    private Integer createShelf(String accessToken, String name, boolean isPublic) {
        Map<String, Object> request = Map.of(
                "name", name,
                "publicShelf", isPublic
        );
        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/shelves",
                auth.bearerEntity(request, accessToken),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        return (Integer) response.getBody().get("id");
    }

    @Test
    void adminCanCreateAndListShelves() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        createShelf(tokens.accessToken(), "My Shelf " + UUID.randomUUID(), false);

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/shelves",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void adminCanCreatePublicShelf() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Integer shelfId = createShelf(tokens.accessToken(), "Public Shelf " + UUID.randomUUID(), true);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/shelves/" + shelfId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("publicShelf")).isEqualTo(true);
    }

    @Test
    void regularUserCannotCreatePublicShelf() {
        BookLoreUserEntity user = auth.createUser("user-shelf", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Map<String, Object> request = Map.of(
                "name", "Public Shelf User " + UUID.randomUUID(),
                "publicShelf", true
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/shelves",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownerCanUpdateAndDeleteShelf() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Integer shelfId = createShelf(tokens.accessToken(), "Update Me Shelf " + UUID.randomUUID(), false);

        Map<String, Object> request = Map.of(
                "name", "Renamed Shelf",
                "publicShelf", false
        );

        ResponseEntity<Map> updateResponse = rest.exchange(
                baseUrl() + "/api/v1/shelves/" + shelfId,
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("name")).isEqualTo("Renamed Shelf");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/shelves/" + shelfId,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void ownerCanViewShelfAndBooks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("shelf-it-");
        LibraryEntity library = data.createLibrary("ShelfBookLib " + UUID.randomUUID(), tempDir);
        var book = data.createBook(library, "Shelf Book");
        Integer shelfId = createShelf(tokens.accessToken(), "Shelf With Books " + UUID.randomUUID(), false);

        Map<String, Object> assignRequest = Map.of(
                "bookIds", Set.of(book.getId()),
                "shelvesToAssign", Set.of(shelfId.longValue()),
                "shelvesToUnassign", Set.of()
        );
        ResponseEntity<List<Map<String, Object>>> assignResponse = rest.exchange(
                baseUrl() + "/api/v1/books/shelves",
                HttpMethod.POST,
                auth.bearerEntity(assignRequest, tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/shelves/" + shelfId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("id")).isEqualTo(shelfId);

        ResponseEntity<List<Map<String, Object>>> booksResponse = rest.exchange(
                baseUrl() + "/api/v1/shelves/" + shelfId + "/books",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(booksResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(booksResponse.getBody()).hasSize(1);
    }

    @Test
    void publicShelfCanBeReadByOtherUser() {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Integer shelfId = createShelf(adminTokens.accessToken(), "Public Readable Shelf " + UUID.randomUUID(), true);

        BookLoreUserEntity user = auth.createUser("user-public-read", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/shelves/" + shelfId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(shelfId);
    }

    @Test
    void privateShelfCannotBeReadByOtherUser() {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Integer shelfId = createShelf(adminTokens.accessToken(), "Private Shelf " + UUID.randomUUID(), false);

        BookLoreUserEntity user = auth.createUser("user-private-read", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/shelves/" + shelfId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void nonOwnerCannotUpdateOrDeleteShelf() {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Integer shelfId = createShelf(adminTokens.accessToken(), "Protected Shelf " + UUID.randomUUID(), true);

        BookLoreUserEntity user = auth.createUser("user-protected", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Map<String, Object> request = Map.of(
                "name", "Hacked Shelf",
                "publicShelf", true
        );

        ResponseEntity<Map> updateResponse = rest.exchange(
                baseUrl() + "/api/v1/shelves/" + shelfId,
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/shelves/" + shelfId,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unauthenticatedRequestReturnsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/v1/shelves", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
