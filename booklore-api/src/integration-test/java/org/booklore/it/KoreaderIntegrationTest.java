package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.util.Md5Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class KoreaderIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    private String koreaderUsername;
    private String koreaderPassword;
    private String bookHash;

    @BeforeEach
    void setUp() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        String unique = UUID.randomUUID().toString();
        koreaderUsername = "koreader-" + unique;
        koreaderPassword = "koreader-password";

        createKoreaderUser(tokens.accessToken());
        enableKoreaderSync(tokens.accessToken());

        Path tempDir = Files.createTempDirectory("koreader-it-");
        LibraryEntity library = data.createLibrary("Koreader Library " + unique, tempDir);

        Path epub = Files.createTempFile("koreader-book-" + unique + "-", ".epub");
        FixtureFactory.writeEpub(epub);
        BookEntity book = data.createBookWithFile(library, "Koreader Test Book " + unique, BookFileType.EPUB, epub);

        bookHash = UUID.randomUUID().toString();
        BookFileEntity primaryFile = book.getBookFiles().get(0);
        primaryFile.setCurrentHash(bookHash);
        bookRepository.save(book);

        Files.deleteIfExists(epub);
    }

    private void createKoreaderUser(String accessToken) {
        Map<String, String> request = Map.of(
                "username", koreaderUsername,
                "password", koreaderPassword
        );
        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me",
                HttpMethod.PUT,
                auth.bearerEntity(request, accessToken),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void enableKoreaderSync(String accessToken) {
        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/koreader-users/me/sync?enabled=true",
                HttpMethod.PATCH,
                auth.bearerEntity(accessToken),
                Void.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private HttpHeaders koreaderHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-auth-user", koreaderUsername);
        headers.set("x-auth-key", Md5Util.md5Hex(koreaderPassword));
        return headers;
    }

    @Test
    void authorizeUserReturnsUsername() {
        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/koreader/users/auth",
                HttpMethod.GET,
                new HttpEntity<>(koreaderHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("username", koreaderUsername);
    }

    @Test
    void putAndGetProgressRoundTrip() {
        Map<String, Object> progress = Map.of(
                "document", bookHash,
                "progress", "/body/DocFragment[0]",
                "percentage", 0.25f,
                "device", "BookLore",
                "device_id", "BookLore"
        );

        ResponseEntity<Map> putResponse = rest.exchange(
                baseUrl() + "/api/koreader/syncs/progress",
                HttpMethod.PUT,
                new HttpEntity<>(progress, koreaderHeaders()),
                Map.class
        );

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putResponse.getBody()).containsEntry("status", "progress updated");

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/koreader/syncs/progress/" + bookHash,
                HttpMethod.GET,
                new HttpEntity<>(koreaderHeaders()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsEntry("document", bookHash);
        assertThat(getResponse.getBody().get("percentage")).isEqualTo(0.25);
    }
}
