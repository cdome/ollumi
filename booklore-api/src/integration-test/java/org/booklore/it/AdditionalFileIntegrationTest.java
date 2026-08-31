package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.dto.request.DetachBookFileRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AdditionalFileIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanListUploadDetachDownloadAndDeleteAdditionalFiles() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("additional-file-it-");
        LibraryEntity library = data.createLibrary("AdditionalFileLib " + UUID.randomUUID(), tempDir);

        Path primaryPdf = Files.createTempFile("primary-", ".pdf");
        FixtureFactory.writePdf(primaryPdf);
        BookEntity book = data.createBookWithFile(
                library, "Additional Book " + UUID.randomUUID(), BookFileType.PDF, primaryPdf);

        ResponseEntity<List<Map<String, Object>>> initialList = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/files",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(initialList.getStatusCode()).isEqualTo(HttpStatus.OK);
        int initialSize = initialList.getBody().size();

        Path additionalCbz = Files.createTempFile("additional-", ".cbz");
        FixtureFactory.writeCbz(additionalCbz);

        MultiValueMap<String, Object> uploadParts = new LinkedMultiValueMap<>();
        uploadParts.add("file", new FileSystemResource(additionalCbz));
        uploadParts.add("isBook", true);

        HttpHeaders uploadHeaders = auth.bearerHeaders(tokens.accessToken());
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> uploadEntity = new HttpEntity<>(uploadParts, uploadHeaders);

        ResponseEntity<Map> uploadResponse = rest.postForEntity(
                baseUrl() + "/api/v1/books/" + book.getId() + "/files",
                uploadEntity,
                Map.class
        );
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer additionalFileId = (Integer) uploadResponse.getBody().get("id");
        assertThat(additionalFileId).isNotNull();

        ResponseEntity<List<Map<String, Object>>> listAfterUpload = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/files",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(listAfterUpload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listAfterUpload.getBody()).hasSize(initialSize + 1);

        DetachBookFileRequest detachRequest = new DetachBookFileRequest(false);
        ResponseEntity<Map> detachResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/files/" + additionalFileId + "/detach",
                HttpMethod.POST,
                auth.bearerEntity(detachRequest, tokens.accessToken()),
                Map.class
        );
        assertThat(detachResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> detachBody = detachResponse.getBody();
        assertThat(detachBody).isNotNull();
        assertThat(detachBody).containsKey("newBook");

        Map<String, Object> newBook = (Map<String, Object>) detachBody.get("newBook");
        assertThat(newBook).containsKey("id");
        assertThat(newBook).containsKey("primaryFile");
        Long newBookId = ((Number) newBook.get("id")).longValue();
        Map<String, Object> primaryFile = (Map<String, Object>) newBook.get("primaryFile");
        Integer detachedFileId = ((Number) primaryFile.get("id")).intValue();

        ResponseEntity<List<Map<String, Object>>> listAfterDetach = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/files",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(listAfterDetach.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listAfterDetach.getBody()).hasSize(initialSize);

        ResponseEntity<byte[]> downloadResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + newBookId + "/files/" + detachedFileId + "/download",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                byte[].class
        );
        assertThat(downloadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(downloadResponse.getBody()).isNotEmpty();

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + newBookId + "/files/" + detachedFileId,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
