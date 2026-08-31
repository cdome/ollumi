package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

public class KoboLocalIntegrationTest extends RestApiIntegrationTest {

    private String koboToken;
    private BookEntity book;

    @BeforeEach
    void setUp() throws Exception {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        String unique = UUID.randomUUID().toString();
        BookLoreUserEntity koboUser = auth.createUser("kobo-user-" + unique, "kobo-password",
                p -> p.setPermissionSyncKobo(true));

        AuthTestHelper.Tokens koboTokens = auth.login(baseUrl(), koboUser.getUsername(), "kobo-password");
        koboToken = generateKoboToken(koboTokens.accessToken());

        Path tempDir = Files.createTempDirectory("kobo-it-");
        LibraryEntity library = data.createLibrary("Kobo Library " + unique, tempDir);
        data.assignLibraryToUser(koboUser, library);

        Path epub = Files.createTempFile("kobo-book-", ".epub");
        FixtureFactory.writeEpub(epub);
        book = data.createBookWithFile(library, "Kobo Test Book", BookFileType.EPUB, epub);
        Files.deleteIfExists(epub);

        // Add enough EPUB books to the Kobo shelf so the first sync page is partial,
        // avoiding the external Kobo store proxy.
        addBooksToKoboShelf(adminTokens.accessToken(), koboTokens.accessToken(), library);
    }

    private String generateKoboToken(String accessToken) {
        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/kobo-settings/token",
                HttpMethod.PUT,
                auth.bearerEntity(accessToken),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
        return (String) response.getBody().get("token");
    }

    private void addBooksToKoboShelf(String adminToken, String koboToken, LibraryEntity library) throws Exception {
        Integer koboShelfId = findKoboShelfId(koboToken);

        for (int i = 0; i < 5; i++) {
            Path epub = Files.createTempFile("kobo-extra-book-" + i + "-", ".epub");
            FixtureFactory.writeEpub(epub);
            BookEntity extraBook = data.createBookWithFile(library, "Kobo Extra Book " + i, BookFileType.EPUB, epub);
            Files.deleteIfExists(epub);
            assignBookToShelf(koboToken, extraBook.getId(), koboShelfId);
        }
        assignBookToShelf(koboToken, book.getId(), koboShelfId);
    }

    private Integer findKoboShelfId(String accessToken) {
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/shelves",
                HttpMethod.GET,
                auth.bearerEntity(accessToken),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        return response.getBody().stream()
                .filter(s -> "Kobo".equals(s.get("name")))
                .findFirst()
                .map(s -> (Integer) s.get("id"))
                .orElseThrow(() -> new IllegalStateException("Kobo shelf not found"));
    }

    private void assignBookToShelf(String accessToken, Long bookId, Integer shelfId) {
        Map<String, Object> request = Map.of(
                "bookIds", Set.of(bookId),
                "shelvesToAssign", Set.of(shelfId.longValue()),
                "shelvesToUnassign", Set.of()
        );
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/books/shelves",
                HttpMethod.POST,
                auth.bearerEntity(request, accessToken),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void initializationReturnsResources() {
        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/kobo/" + koboToken + "/v1/initialization",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("Resources");
    }

    @Test
    void librarySyncReturnsEntitlements() {
        ResponseEntity<List> response = rest.exchange(
                baseUrl() + "/api/kobo/" + koboToken + "/v1/library/sync",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void deviceAuthReturnsTokens() {
        Map<String, String> request = Map.of("UserKey", "test-user-key");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/kobo/" + koboToken + "/v1/auth/device",
                HttpMethod.POST,
                new HttpEntity<>(request),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("AccessToken");
        assertThat(response.getBody()).containsKey("RefreshToken");
    }

    @Test
    void getReadingStateReturnsState() {
        ResponseEntity<List> response = rest.exchange(
                baseUrl() + "/api/kobo/" + koboToken + "/v1/library/" + book.getId() + "/state",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void putReadingStateUpdatesState() {
        Map<String, Object> readingState = Map.of(
                "EntitlementId", String.valueOf(book.getId()),
                "CurrentBookmark", Map.of("ProgressPercent", 50.0)
        );
        Map<String, Object> request = Map.of("ReadingStates", List.of(readingState));

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/kobo/" + koboToken + "/v1/library/" + book.getId() + "/state",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("requestResult");
    }

    @Test
    void downloadBookReturnsFile() {
        ResponseEntity<byte[]> response = rest.exchange(
                baseUrl() + "/api/kobo/" + koboToken + "/v1/books/" + book.getId() + "/download",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                byte[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isGreaterThan(0);
    }
}
