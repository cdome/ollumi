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

public class AnnotationIntegrationTest extends RestApiIntegrationTest {

    private BookEntity seedBook() throws Exception {
        Path tempDir = Files.createTempDirectory("annotation-it-");
        LibraryEntity library = data.createLibrary("AnnotationLib " + UUID.randomUUID(), tempDir);
        return data.createBook(library, "Book With Annotations");
    }

    @Test
    void userCanCreateAndListAnnotations() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/1:0",
                "text", "Highlighted text",
                "color", "#FFFF00",
                "style", "highlight",
                "note", "A note",
                "chapterTitle", "Chapter One"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/annotations",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).containsKey("id");
        assertThat(createResponse.getBody().get("bookId")).isEqualTo(book.getId().intValue());
        assertThat(createResponse.getBody().get("text")).isEqualTo("Highlighted text");
        assertThat(createResponse.getBody().get("style")).isEqualTo("highlight");

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/annotations/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
    }

    @Test
    void userCanGetAnnotationById() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/2:0",
                "text", "By id",
                "style", "underline"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/annotations",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer annotationId = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/annotations/" + annotationId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("id")).isEqualTo(annotationId);
        assertThat(getResponse.getBody().get("text")).isEqualTo("By id");
    }

    @Test
    void userCanUpdateAnnotation() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/3:0",
                "text", "Original text",
                "style", "highlight"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/annotations",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer annotationId = (Integer) createResponse.getBody().get("id");

        Map<String, Object> updateRequest = Map.of(
                "color", "#00FF00",
                "style", "underline",
                "note", "Updated note"
        );

        ResponseEntity<Map> updateResponse = rest.exchange(
                baseUrl() + "/api/v1/annotations/" + annotationId,
                HttpMethod.PUT,
                auth.bearerEntity(updateRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("id")).isEqualTo(annotationId);
        assertThat(updateResponse.getBody().get("color")).isEqualTo("#00FF00");
        assertThat(updateResponse.getBody().get("style")).isEqualTo("underline");
        assertThat(updateResponse.getBody().get("note")).isEqualTo("Updated note");
    }

    @Test
    void userCanDeleteAnnotation() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/4:0",
                "text", "Delete me",
                "style", "strikethrough"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/annotations",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer annotationId = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/annotations/" + annotationId,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/annotations/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getBody()).isEmpty();
    }

    @Test
    void annotationRequiresCfiAndText() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "style", "highlight"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/annotations",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void annotationValidatesStyle() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/5:0",
                "text", "Text",
                "style", "invalid-style"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/annotations",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void otherUserCannotSeeAnnotations() throws Exception {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/6:0",
                "text", "Private",
                "style", "highlight"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/annotations",
                auth.bearerEntity(createRequest, adminTokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        BookLoreUserEntity other = auth.createUser("user-annotation-other", "password");
        AuthTestHelper.Tokens otherTokens = auth.login(baseUrl(), other.getUsername(), "password");

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/annotations/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(otherTokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isEmpty();
    }

    @Test
    void unauthenticatedRequestReturnsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/v1/annotations/book/1", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
