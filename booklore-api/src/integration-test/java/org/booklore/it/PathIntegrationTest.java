package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookLoreUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PathIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanBrowseFoldersAtPath() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("path-it-");
        Path subDir1 = Files.createDirectory(tempDir.resolve("alpha"));
        Path subDir2 = Files.createDirectory(tempDir.resolve("beta"));
        Files.createFile(tempDir.resolve("file.txt"));

        try {
            ResponseEntity<List<String>> response = rest.exchange(
                    baseUrl() + "/api/v1/path?path=" + tempDir.toAbsolutePath(),
                    HttpMethod.GET,
                    auth.bearerEntity(tokens.accessToken()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsExactlyInAnyOrder(
                    subDir1.toAbsolutePath().toString(),
                    subDir2.toAbsolutePath().toString()
            );
            assertThat(response.getBody()).doesNotContain(tempDir.resolve("file.txt").toAbsolutePath().toString());
        } finally {
            Files.deleteIfExists(tempDir.resolve("file.txt"));
            Files.deleteIfExists(subDir1);
            Files.deleteIfExists(subDir2);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void nonExistentPathReturnsEmptyList() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<List<String>> response = rest.exchange(
                baseUrl() + "/api/v1/path?path=/does/not/exist",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void regularUserCannotBrowsePaths() {
        BookLoreUserEntity user = auth.createUser("path-no-perm", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/path?path=/tmp",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void userWithLibraryManagePermissionCanBrowsePaths() throws Exception {
        BookLoreUserEntity user = auth.createUser("path-lib-manager", "password",
                perms -> perms.setPermissionManageLibrary(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Path tempDir = Files.createTempDirectory("path-it-lib-");
        try {
            ResponseEntity<List<String>> response = rest.exchange(
                    baseUrl() + "/api/v1/path?path=" + tempDir.toAbsolutePath(),
                    HttpMethod.GET,
                    auth.bearerEntity(tokens.accessToken()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void blockedSystemPathReturnsBadRequest() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/path?path=/proc",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
