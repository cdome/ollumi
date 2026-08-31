package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookEntity;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class BookmarkIntegrationTest extends RestApiIntegrationTest {

    private BookEntity seedBook() throws Exception {
        Path tempDir = Files.createTempDirectory("bookmark-it-");
        LibraryEntity library = data.createLibrary("BookmarkLib " + UUID.randomUUID(), tempDir);
        return data.createBook(library, "Book With Bookmarks");
    }

    @Test
    void userCanCreateAndListBookmarks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "cfi", "/4/2!/1:0",
                "title", "First bookmark"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/bookmarks",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).containsKey("id");
        assertThat(createResponse.getBody().get("bookId")).isEqualTo(book.getId().intValue());
        assertThat(createResponse.getBody().get("cfi")).isEqualTo("/4/2!/1:0");
        assertThat(createResponse.getBody().get("title")).isEqualTo("First bookmark");

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/bookmarks/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
    }

    @Test
    void userCanGetBookmarkById() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "cfi", "/4/2!/2:0",
                "title", "By id"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/bookmarks",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer bookmarkId = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/bookmarks/" + bookmarkId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("id")).isEqualTo(bookmarkId);
        assertThat(getResponse.getBody().get("title")).isEqualTo("By id");
    }

    @Test
    void userCanUpdateBookmark() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "cfi", "/4/2!/3:0",
                "title", "Original"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/bookmarks",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer bookmarkId = (Integer) createResponse.getBody().get("id");

        Map<String, Object> updateRequest = Map.of(
                "title", "Renamed bookmark",
                "cfi", "/4/2!/4:0",
                "color", "#123456",
                "notes", "A note",
                "priority", 2
        );

        ResponseEntity<Map> updateResponse = rest.exchange(
                baseUrl() + "/api/v1/bookmarks/" + bookmarkId,
                HttpMethod.PUT,
                auth.bearerEntity(updateRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("id")).isEqualTo(bookmarkId);
        assertThat(updateResponse.getBody().get("title")).isEqualTo("Renamed bookmark");
        assertThat(updateResponse.getBody().get("cfi")).isEqualTo("/4/2!/4:0");
        assertThat(updateResponse.getBody().get("color")).isEqualTo("#123456");
        assertThat(updateResponse.getBody().get("notes")).isEqualTo("A note");
        assertThat(updateResponse.getBody().get("priority")).isEqualTo(2);
    }

    @Test
    void userCanDeleteBookmark() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "cfi", "/4/2!/5:0",
                "title", "Delete me"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/bookmarks",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer bookmarkId = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/bookmarks/" + bookmarkId,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/bookmarks/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getBody()).isEmpty();
    }

    @Test
    void bookmarkRequiresBookId() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> request = Map.of(
                "cfi", "/4/2!/1:0",
                "title", "No book"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/bookmarks",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void bookmarkValidatesColorFormat() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "cfi", "/4/2!/6:0",
                "title", "Bad color"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/bookmarks",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer bookmarkId = (Integer) createResponse.getBody().get("id");

        Map<String, Object> updateRequest = Map.of("color", "red");

        ResponseEntity<Map> updateResponse = rest.exchange(
                baseUrl() + "/api/v1/bookmarks/" + bookmarkId,
                HttpMethod.PUT,
                auth.bearerEntity(updateRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void otherUserCannotSeeBookmarks() throws Exception {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "cfi", "/4/2!/7:0",
                "title", "Private"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/bookmarks",
                auth.bearerEntity(createRequest, adminTokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        BookLoreUserEntity other = auth.createUser("user-bookmark-other", "password");
        AuthTestHelper.Tokens otherTokens = auth.login(baseUrl(), other.getUsername(), "password");

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/bookmarks/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(otherTokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isEmpty();
    }

    @Test
    void unauthenticatedRequestReturnsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/v1/bookmarks/book/1", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
