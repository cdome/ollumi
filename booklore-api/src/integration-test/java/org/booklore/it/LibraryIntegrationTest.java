package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
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

public class LibraryIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanCreateAndRetrieveLibrary() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("library-it-");

        Map<String, Object> request = Map.of(
                "name", "Library " + UUID.randomUUID(),
                "paths", List.of(Map.of("path", tempDir.toString())),
                "metadataSource", "EMBEDDED",
                "organizationMode", "AUTO_DETECT",
                "watch", false
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/libraries",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).containsKey("id");

        Integer libraryId = (Integer) createResponse.getBody().get("id");

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/libraries/" + libraryId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("id")).isEqualTo(libraryId);
    }

    @Test
    void adminCanListLibraries() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("library-it-");
        data.createLibrary("ListLib " + UUID.randomUUID(), tempDir);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/libraries",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void adminCanUpdateLibrary() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("library-it-");
        LibraryEntity library = data.createLibrary("UpdateLib " + UUID.randomUUID(), tempDir);

        Map<String, Object> request = Map.of(
                "name", "Updated Name",
                "paths", List.of(Map.of("path", tempDir.toString())),
                "metadataSource", "EMBEDDED",
                "organizationMode", "AUTO_DETECT",
                "watch", false
        );

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/libraries/" + library.getId(),
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Updated Name");
    }

    @Test
    void adminCanDeleteLibrary() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("library-it-");
        LibraryEntity library = data.createLibrary("DeleteLib " + UUID.randomUUID(), tempDir);

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/libraries/" + library.getId(),
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void adminCanRescanLibrary() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("library-it-");
        LibraryEntity library = data.createLibrary("RescanLib " + UUID.randomUUID(), tempDir);

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/libraries/" + library.getId() + "/refresh",
                HttpMethod.PUT,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void adminCanSetFileNamingPattern() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("library-it-");
        LibraryEntity library = data.createLibrary("PatternLib " + UUID.randomUUID(), tempDir);

        Map<String, Object> request = Map.of("fileNamingPattern", "{title}");
        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/libraries/" + library.getId() + "/file-naming-pattern",
                HttpMethod.PATCH,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("fileNamingPattern")).isEqualTo("{title}");
    }

    @Test
    void adminCanScanLibraryPaths() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("library-it-");

        Map<String, Object> request = Map.of(
                "name", "ScanLib " + UUID.randomUUID(),
                "paths", List.of(Map.of("path", tempDir.toString())),
                "metadataSource", "EMBEDDED",
                "organizationMode", "AUTO_DETECT",
                "watch", false
        );

        ResponseEntity<Integer> response = rest.postForEntity(
                baseUrl() + "/api/v1/libraries/scan",
                auth.bearerEntity(request, tokens.accessToken()),
                Integer.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void formatCountsForEmptyLibrary() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("library-it-");
        LibraryEntity library = data.createLibrary("FormatLib " + UUID.randomUUID(), tempDir);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/libraries/" + library.getId() + "/format-counts",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}
