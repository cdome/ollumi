package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.entity.LibraryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class FileUploadIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanUploadFileToLibrary() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("file-upload-it-");
        LibraryEntity library = data.createLibrary("UploadLib " + UUID.randomUUID(), tempDir);

        Path pdf = Files.createTempFile("upload-", ".pdf");
        FixtureFactory.writePdf(pdf);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new FileSystemResource(pdf));
        parts.add("libraryId", library.getId());
        parts.add("pathId", library.getLibraryPaths().get(0).getId());

        HttpHeaders headers = auth.bearerHeaders(tokens.accessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(parts, headers);

        ResponseEntity<Void> response = rest.postForEntity(
                baseUrl() + "/api/v1/files/upload", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void adminCanUploadFileToBookdrop() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path pdf = Files.createTempFile("bookdrop-upload-", ".pdf");
        FixtureFactory.writePdf(pdf);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new FileSystemResource(pdf));

        HttpHeaders headers = auth.bearerHeaders(tokens.accessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(parts, headers);

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/files/upload/bookdrop", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNull();
    }
}
