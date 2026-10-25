package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.AppSettingKey;
import org.booklore.repository.jooq.JooqBookdropFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class BookdropIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private JooqBookdropFileRepository bookdropFileRepository;

    @BeforeEach
    void prepareBookdrop() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        List<Map<String, Object>> settingsUpdate = List.of(Map.of(
                "name", AppSettingKey.METADATA_DOWNLOAD_ON_BOOKDROP.name(),
                "value", false
        ));
        ResponseEntity<Void> settingsResponse = rest.exchange(
                baseUrl() + "/api/v1/settings",
                HttpMethod.PUT,
                auth.bearerEntity(settingsUpdate, tokens.accessToken()),
                Void.class
        );
        assertThat(settingsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Path bookdrop = Path.of("build/tmp/it-bookdrop");
        if (Files.exists(bookdrop)) {
            try (var walk = Files.walk(bookdrop)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                if (!p.equals(bookdrop)) {
                                    Files.deleteIfExists(p);
                                }
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
        bookdropFileRepository.deleteAllById(bookdropFileRepository.findAllIds());

        TimeUnit.MILLISECONDS.sleep(300);
    }

    @Test
    void adminCanScanNotifyExtractEditAndDiscardBookdropFiles() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        String unique = UUID.randomUUID().toString();
        String fileName = "Bookdrop Test " + unique + ".pdf";

        Path bookdrop = Path.of("build/tmp/it-bookdrop");
        Files.createDirectories(bookdrop);
        Path fixture = bookdrop.resolve(fileName);
        FixtureFactory.writePdf(fixture);

        ResponseEntity<Void> rescanResponse = rest.exchange(
                baseUrl() + "/api/v1/bookdrop/rescan",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );
        assertThat(rescanResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> pendingFiles = waitForPendingFiles(tokens.accessToken(), 10);
        assertThat(pendingFiles).hasSizeGreaterThanOrEqualTo(1);

        Long fileId = Long.valueOf(pendingFiles.get(0).get("id").toString());

        ResponseEntity<Map> notificationResponse = rest.exchange(
                baseUrl() + "/api/v1/bookdrop/notification",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(notificationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notificationResponse.getBody()).containsKey("pendingCount");
        assertThat((Integer) notificationResponse.getBody().get("pendingCount")).isGreaterThanOrEqualTo(1);

        ResponseEntity<Map> filesResponse = rest.exchange(
                baseUrl() + "/api/v1/bookdrop/files?status=pending&size=50",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(filesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filesResponse.getBody()).containsKey("content");

        Map<String, Object> extractRequest = Map.of(
                "pattern", "{Title}",
                "selectAll", false,
                "selectedIds", List.of(fileId),
                "preview", true
        );
        ResponseEntity<Map> extractResponse = rest.exchange(
                baseUrl() + "/api/v1/bookdrop/files/extract-pattern",
                HttpMethod.POST,
                auth.bearerEntity(extractRequest, tokens.accessToken()),
                Map.class
        );
        assertThat(extractResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractResponse.getBody()).containsKey("results");

        BookMetadata updates = new BookMetadata();
        updates.setPublisher("IT Publisher");
        Map<String, Object> bulkEditRequest = Map.of(
                "fields", updates,
                "enabledFields", Set.of("publisher"),
                "mergeArrays", false,
                "selectAll", false,
                "selectedIds", List.of(fileId)
        );
        ResponseEntity<Map> bulkEditResponse = rest.exchange(
                baseUrl() + "/api/v1/bookdrop/files/bulk-edit",
                HttpMethod.POST,
                auth.bearerEntity(bulkEditRequest, tokens.accessToken()),
                Map.class
        );
        assertThat(bulkEditResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bulkEditResponse.getBody()).containsKey("totalFiles");

        Map<String, Object> discardRequest = Map.of(
                "selectAll", false,
                "selectedIds", List.of(fileId)
        );
        ResponseEntity<Void> discardResponse = rest.exchange(
                baseUrl() + "/api/v1/bookdrop/files/discard",
                HttpMethod.POST,
                auth.bearerEntity(discardRequest, tokens.accessToken()),
                Void.class
        );
        assertThat(discardResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> afterDiscard = rest.exchange(
                baseUrl() + "/api/v1/bookdrop/files?status=pending&size=50",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(afterDiscard.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) afterDiscard.getBody().get("content");
        assertThat(content).noneMatch(f -> fileId.equals(Long.valueOf(f.get("id").toString())));
    }

    private List<Map<String, Object>> waitForPendingFiles(String token, int maxAttempts) throws InterruptedException {
        for (int i = 0; i < maxAttempts; i++) {
            ResponseEntity<Map> response = rest.exchange(
                    baseUrl() + "/api/v1/bookdrop/files?status=pending&size=50",
                    HttpMethod.GET,
                    auth.bearerEntity(token),
                    new ParameterizedTypeReference<>() {}
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
                if (content != null && !content.isEmpty()) {
                    return content;
                }
            }
            TimeUnit.MILLISECONDS.sleep(600);
        }
        return List.of();
    }
}
