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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityMatrixIntegrationTest extends RestApiIntegrationTest {

    @Test
    void unauthenticatedProtectedRequestReturnsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity(
                baseUrl() + "/api/v1/users/me",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void malformedBearerReturnsUnauthorized() {
        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/me",
                HttpMethod.GET,
                auth.bearerEntity("not-a-real-jwt"),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void regularUserDeniedAdminEndpoint() {
        BookLoreUserEntity user = auth.createUser("sec-user", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void regularUserDeniedLibraryManagement() {
        BookLoreUserEntity user = auth.createUser("sec-lib-user", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Map<String, Object> body = Map.of(
                "name", "Forbidden",
                "paths", List.of(Map.of("path", "/tmp/sec-forbidden")),
                "metadataSource", "EMBEDDED",
                "organizationMode", "AUTO_DETECT",
                "watch", false
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/libraries",
                auth.bearerEntity(body, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void bookAccessDeniedForUnassignedLibrary() throws Exception {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("sec-library-");
        LibraryEntity library = data.createLibrary("SecLib " + System.nanoTime(), tempDir);
        BookEntity book = data.createBook(library, "Secured Book");

        BookLoreUserEntity user = auth.createUser("sec-book-user", "password");
        AuthTestHelper.Tokens userTokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(userTokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void bookAccessAllowedForAssignedLibrary() throws Exception {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("sec-library-allowed-");
        LibraryEntity library = data.createLibrary("SecLibAllowed " + System.nanoTime(), tempDir);
        BookEntity book = data.createBook(library, "Allowed Book");

        BookLoreUserEntity user = auth.createUser("sec-book-allowed", "password");
        data.assignLibraryToUser(user, library);
        AuthTestHelper.Tokens userTokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(userTokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(book.getId().intValue());
    }
}
