package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.dto.settings.AppSettingKey;
import org.booklore.model.dto.sidecar.SidecarMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class SidecarIntegrationTest extends RestApiIntegrationTest {

    @BeforeEach
    void enableSidecarMetadata() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> sidecarSettings = Map.of(
                "enabled", true,
                "writeOnUpdate", false,
                "writeOnScan", false,
                "includeCoverFile", false
        );

        Map<String, Object> persistenceSettings = Map.of(
                "saveToOriginalFile", Map.of(
                        "epub", Map.of("enabled", false, "maxFileSizeInMb", 250),
                        "pdf", Map.of("enabled", false, "maxFileSizeInMb", 250),
                        "cbx", Map.of("enabled", false, "maxFileSizeInMb", 250),
                        "audiobook", Map.of("enabled", false, "maxFileSizeInMb", 250)
                ),
                "convertCbrCb7ToCbz", false,
                "moveFilesToLibraryPattern", false,
                "sidecarSettings", sidecarSettings
        );

        List<Map<String, Object>> settingsUpdate = List.of(Map.of(
                "name", AppSettingKey.METADATA_PERSISTENCE_SETTINGS.name(),
                "value", persistenceSettings
        ));

        ResponseEntity<Void> updateResponse = rest.exchange(
                baseUrl() + "/api/v1/settings",
                HttpMethod.PUT,
                auth.bearerEntity(settingsUpdate, tokens.accessToken()),
                Void.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminCanExportImportAndBulkSyncSidecarMetadata() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("sidecar-it-");
        LibraryEntity library = data.createLibrary("SidecarLib " + UUID.randomUUID(), tempDir);

        Path pdf = Files.createTempFile("sidecar-", ".pdf");
        FixtureFactory.writePdf(pdf);
        BookEntity book = data.createBookWithFile(
                library, "Sidecar Book " + UUID.randomUUID(), BookFileType.PDF, pdf);

        ResponseEntity<Map> exportResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/sidecar/export",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(exportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<SidecarMetadata> getResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/sidecar",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                SidecarMetadata.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getMetadata()).isNotNull();

        ResponseEntity<Map> importResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/sidecar/import",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(importResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> statusResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/sidecar/status",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody()).containsKey("status");

        ResponseEntity<Map> bulkExportResponse = rest.exchange(
                baseUrl() + "/api/v1/libraries/" + library.getId() + "/sidecar/export-all",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(bulkExportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bulkExportResponse.getBody()).containsKey("exported");

        ResponseEntity<Map> bulkImportResponse = rest.exchange(
                baseUrl() + "/api/v1/libraries/" + library.getId() + "/sidecar/import-all",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(bulkImportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bulkImportResponse.getBody()).containsKey("imported");
    }
}
