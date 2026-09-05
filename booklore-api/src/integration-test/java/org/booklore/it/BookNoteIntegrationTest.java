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

public class BookNoteIntegrationTest extends RestApiIntegrationTest {

    private BookEntity seedBook() throws Exception {
        Path tempDir = Files.createTempDirectory("booknote-it-");
        LibraryEntity library = data.createLibrary("BookNoteLib " + UUID.randomUUID(), tempDir);
        return data.createBook(library, "Book With Notes");
    }

    @Test
    void userCanCreateAndListBookNotes() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "title", "Note title",
                "content", "This is a note."
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/book-notes",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).containsKey("id");
        assertThat(createResponse.getBody().get("bookId")).isEqualTo(book.getId().intValue());
        assertThat(createResponse.getBody().get("title")).isEqualTo("Note title");
        assertThat(createResponse.getBody().get("content")).isEqualTo("This is a note.");

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/book-notes/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody().get(0).get("content")).isEqualTo("This is a note.");
    }

    @Test
    void userCanUpdateBookNote() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "title", "Original",
                "content", "Original content"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/book-notes",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer noteId = (Integer) createResponse.getBody().get("id");

        Map<String, Object> updateRequest = Map.of(
                "id", noteId,
                "bookId", book.getId(),
                "title", "Updated",
                "content", "Updated content"
        );

        ResponseEntity<Map> updateResponse = rest.postForEntity(
                baseUrl() + "/api/v1/book-notes",
                auth.bearerEntity(updateRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("id")).isEqualTo(noteId);
        assertThat(updateResponse.getBody().get("content")).isEqualTo("Updated content");
    }

    @Test
    void userCanDeleteBookNote() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "title", "To delete",
                "content", "Delete me"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/book-notes",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer noteId = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/book-notes/" + noteId,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/book-notes/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getBody()).isEmpty();
    }

    @Test
    void bookNoteRequiresContent() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "title", "Missing content"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/book-notes",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void bookNoteRequiresBookId() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> request = Map.of(
                "title", "No book",
                "content", "Content"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/book-notes",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void otherUserCannotSeeBookNotes() throws Exception {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "title", "Private note",
                "content", "Private content"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/book-notes",
                auth.bearerEntity(createRequest, adminTokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        BookLoreUserEntity other = auth.createUser("user-booknote-other", "password");
        AuthTestHelper.Tokens otherTokens = auth.login(baseUrl(), other.getUsername(), "password");

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/book-notes/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(otherTokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isEmpty();
    }

    @Test
    void unauthenticatedRequestReturnsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/v1/book-notes/book/1", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
