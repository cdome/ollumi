package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class KomgaIntegrationTest extends RestApiIntegrationTest {

    private String opdsUsername;
    private String opdsPassword;
    private BookEntity book;

    @BeforeEach
    void setUp() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        enableKomga(tokens.accessToken());

        String unique = UUID.randomUUID().toString();
        opdsUsername = "komga-" + unique;
        opdsPassword = "komga-password";

        BookLoreUserEntity user = auth.createUser("komga-user-" + unique, "password",
                p -> p.setPermissionAccessOpds(true));
        data.createOpdsUser(user, opdsUsername, opdsPassword);

        Path tempDir = Files.createTempDirectory("komga-it-");
        LibraryEntity library = data.createLibrary("Komga Library " + unique, tempDir);

        Path cbz = Files.createTempFile("komga-book-" + unique + "-", ".cbz");
        FixtureFactory.writeCbz(cbz);
        book = data.createBookWithFile(library, "Komga Test Book " + unique, BookFileType.CBX, cbz);
        Files.deleteIfExists(cbz);
    }

    private void enableKomga(String accessToken) {
        List<Map<String, Object>> settings = List.of(
                Map.of("name", "KOMGA_API_ENABLED", "value", "true")
        );
        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/settings",
                HttpMethod.PUT,
                auth.bearerEntity(settings, accessToken),
                Void.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders komgaHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(opdsUsername, opdsPassword);
        return headers;
    }

    @Test
    void librariesEndpointReturnsJson() {
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/komga/api/v1/libraries",
                HttpMethod.GET,
                new HttpEntity<>(komgaHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }

    @Test
    void libraryByIdReturnsJson() {
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/komga/api/v1/libraries/" + book.getLibrary().getId(),
                HttpMethod.GET,
                new HttpEntity<>(komgaHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }

    @Test
    void booksEndpointReturnsJson() {
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/komga/api/v1/books",
                HttpMethod.GET,
                new HttpEntity<>(komgaHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }

    @Test
    void bookByIdReturnsJson() {
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/komga/api/v1/books/" + book.getId(),
                HttpMethod.GET,
                new HttpEntity<>(komgaHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }
}
