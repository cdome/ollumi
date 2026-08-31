package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class PdfAnnotationIntegrationTest extends RestApiIntegrationTest {

    private BookEntity seedBook() throws Exception {
        Path tempDir = Files.createTempDirectory("pdfannotation-it-");
        LibraryEntity library = data.createLibrary("PdfAnnotationLib " + UUID.randomUUID(), tempDir);
        return data.createBook(library, "Book With Pdf Annotations");
    }

    @Test
    void userCanSaveAndRetrievePdfAnnotations() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();
        String annotationData = "{\"annotations\":[{\"page\":1,\"x\":10,\"y\":20}]}";

        Map<String, Object> request = Map.of("data", annotationData);

        ResponseEntity<Void> saveResponse = rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        assertThat(saveResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("data")).isEqualTo(annotationData);
    }

    @Test
    void userCanUpdatePdfAnnotations() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> firstRequest = Map.of("data", "{\"version\":1}");
        rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.PUT,
                auth.bearerEntity(firstRequest, tokens.accessToken()),
                Void.class
        );

        Map<String, Object> secondRequest = Map.of("data", "{\"version\":2}");
        ResponseEntity<Void> updateResponse = rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.PUT,
                auth.bearerEntity(secondRequest, tokens.accessToken()),
                Void.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getBody().get("data")).isEqualTo("{\"version\":2}");
    }

    @Test
    void userCanDeletePdfAnnotations() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of("data", "{\"annotations\":[]}");
        rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getResponse.getBody()).isNull();
    }

    @Test
    void missingPdfAnnotationsReturnsNoContent() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getResponse.getBody()).isNull();
    }

    @Test
    void otherUserCannotSeePdfAnnotations() throws Exception {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookEntity book = seedBook();

        Map<String, Object> request = Map.of("data", "{\"private\":true}");
        rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.PUT,
                auth.bearerEntity(request, adminTokens.accessToken()),
                Void.class
        );

        BookLoreUserEntity other = auth.createUser("user-pdf-other", "password");
        AuthTestHelper.Tokens otherTokens = auth.login(baseUrl(), other.getUsername(), "password");

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/pdf-annotations/book/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(otherTokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unauthenticatedRequestReturnsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/v1/pdf-annotations/book/1", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
