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

public class BookNoteV2IntegrationTest extends RestApiIntegrationTest {

    private BookEntity seedBook() throws Exception {
        Path tempDir = Files.createTempDirectory("booknotev2-it-");
        LibraryEntity library = data.createLibrary("BookNoteV2Lib " + UUID.randomUUID(), tempDir);
        return data.createBook(library, "Book With V2 Notes");
    }

    @Test
    void userCanCreateAndListV2Notes() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/1:0",
                "selectedText", "Selected text",
                "noteContent", "A note about this passage.",
                "color", "#FFCC00",
                "chapterTitle", "Chapter One"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v2/book-notes",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).containsKey("id");
        assertThat(createResponse.getBody().get("bookId")).isEqualTo(book.getId().intValue());
        assertThat(createResponse.getBody().get("cfi")).isEqualTo("/6/2!/1:0");
        assertThat(createResponse.getBody().get("noteContent")).isEqualTo("A note about this passage.");
        assertThat(createResponse.getBody().get("color")).isEqualTo("#FFCC00");

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v2/book-notes/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
    }

    @Test
    void userCanGetV2NoteById() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/2:0",
                "selectedText", "Text",
                "noteContent", "Note",
                "color", "#FFCC00"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v2/book-notes",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer noteId = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v2/book-notes/" + noteId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("id")).isEqualTo(noteId);
        assertThat(getResponse.getBody().get("noteContent")).isEqualTo("Note");
    }

    @Test
    void userCanUpdateV2Note() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/3:0",
                "selectedText", "Original",
                "noteContent", "Original note",
                "color", "#FFCC00"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v2/book-notes",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer noteId = (Integer) createResponse.getBody().get("id");

        Map<String, Object> updateRequest = Map.of(
                "noteContent", "Updated note",
                "color", "#00FF00",
                "chapterTitle", "Updated chapter"
        );

        ResponseEntity<Map> updateResponse = rest.exchange(
                baseUrl() + "/api/v2/book-notes/" + noteId,
                HttpMethod.PUT,
                auth.bearerEntity(updateRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("id")).isEqualTo(noteId);
        assertThat(updateResponse.getBody().get("noteContent")).isEqualTo("Updated note");
        assertThat(updateResponse.getBody().get("color")).isEqualTo("#00FF00");
        assertThat(updateResponse.getBody().get("chapterTitle")).isEqualTo("Updated chapter");
    }

    @Test
    void userCanDeleteV2Note() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/4:0",
                "selectedText", "Delete",
                "noteContent", "Delete me",
                "color", "#FFCC00"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v2/book-notes",
                auth.bearerEntity(createRequest, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer noteId = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v2/book-notes/" + noteId,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void v2NoteRequiresCfi() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "noteContent", "Missing CFI"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v2/book-notes",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void v2NoteRequiresValidColor() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/5:0",
                "noteContent", "Bad color",
                "color", "red"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v2/book-notes",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void otherUserCannotSeeV2Notes() throws Exception {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> createRequest = Map.of(
                "bookId", book.getId(),
                "cfi", "/6/2!/6:0",
                "selectedText", "Private",
                "noteContent", "Private note",
                "color", "#FFCC00"
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v2/book-notes",
                auth.bearerEntity(createRequest, adminTokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        BookLoreUserEntity other = auth.createUser("user-booknotev2-other", "password");
        AuthTestHelper.Tokens otherTokens = auth.login(baseUrl(), other.getUsername(), "password");

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v2/book-notes/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(otherTokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isEmpty();
    }

    @Test
    void unauthenticatedRequestReturnsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/v2/book-notes/book/1", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
