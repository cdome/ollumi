package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.ShelfEntity;
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

public class OpdsIntegrationTest extends RestApiIntegrationTest {

    private String opdsUsername;
    private String opdsPassword;
    private BookEntity book;

    @BeforeEach
    void setUp() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        enableOpds(tokens.accessToken());

        String unique = UUID.randomUUID().toString();
        opdsUsername = "opds-" + unique;
        opdsPassword = "opds-password";

        BookLoreUserEntity user = auth.createUser("opds-user-" + unique, "password",
                p -> p.setPermissionAccessOpds(true));

        Path tempDir = Files.createTempDirectory("opds-it-");
        LibraryEntity library = data.createLibrary("OPDS Library " + unique, tempDir);
        data.assignLibraryToUser(user, library);

        data.createOpdsUser(user, opdsUsername, opdsPassword);

        Path cbz = Files.createTempFile("opds-book-" + unique + "-", ".cbz");
        FixtureFactory.writeCbz(cbz);
        book = data.createBookWithFile(library, "OPDS Test Book " + unique, BookFileType.CBX, cbz);
        Files.deleteIfExists(cbz);

        ShelfEntity shelf = data.createShelf(user, "OPDS Shelf " + unique, false);
        data.addBookToShelf(book, shelf);
    }

    private void enableOpds(String accessToken) {
        List<Map<String, Object>> settings = List.of(
                Map.of("name", "OPDS_SERVER_ENABLED", "value", "true")
        );
        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/settings",
                HttpMethod.PUT,
                auth.bearerEntity(settings, accessToken),
                Void.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders opdsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(opdsUsername, opdsPassword);
        return headers;
    }

    @Test
    void rootCatalogReturnsAtomFeed() {
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/api/v1/opds",
                HttpMethod.GET,
                new HttpEntity<>(opdsHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/atom+xml");
    }

    @Test
    void librariesNavigationReturnsAtomFeed() {
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/api/v1/opds/libraries",
                HttpMethod.GET,
                new HttpEntity<>(opdsHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/atom+xml");
    }

    @Test
    void shelvesNavigationReturnsAtomFeed() {
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/api/v1/opds/shelves",
                HttpMethod.GET,
                new HttpEntity<>(opdsHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/atom+xml");
    }

    @Test
    void recentFeedReturnsAtomFeed() {
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/api/v1/opds/recent",
                HttpMethod.GET,
                new HttpEntity<>(opdsHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/atom+xml");
    }

    @Test
    void catalogSearchReturnsAtomFeed() {
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/api/v1/opds/catalog?q=OPDS+Test+Book",
                HttpMethod.GET,
                new HttpEntity<>(opdsHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/atom+xml");
    }

    @Test
    void bookDownloadReturnsFile() {
        ResponseEntity<byte[]> response = rest.exchange(
                baseUrl() + "/api/v1/opds/" + book.getId() + "/download",
                HttpMethod.GET,
                new HttpEntity<>(opdsHeaders()),
                byte[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isGreaterThan(0);
    }

    @Test
    void bookCoverReturnsImage() {
        ResponseEntity<byte[]> response = rest.exchange(
                baseUrl() + "/api/v1/opds/" + book.getId() + "/cover",
                HttpMethod.GET,
                new HttpEntity<>(opdsHeaders()),
                byte[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("image");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isGreaterThan(0);
    }

    @Test
    void searchDescriptionReturnsXml() {
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/api/v1/opds/search.opds",
                HttpMethod.GET,
                new HttpEntity<>(opdsHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("xml");
    }
}
