package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class BookMediaIntegrationTest extends RestApiIntegrationTest {

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("book-media-it-");
        return data.createLibrary("BookMediaITLib " + UUID.randomUUID(), tempDir);
    }

    private Path writeFixtureCover() throws Exception {
        Path cover = Files.createTempFile("cover", ".png");
        return FixtureFactory.writePng(cover);
    }

    private HttpEntity<MultiValueMap<String, Object>> buildMultipartEntity(Path file, String accessToken) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));

        HttpHeaders headers = auth.bearerHeaders(accessToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void adminCanGetBookThumbnailAndCoverWithQueryToken() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        Path fixtureDir = Files.createTempDirectory("fixture-");
        Path pdfFixture = FixtureFactory.writePdf(fixtureDir.resolve("book.pdf"));
        BookEntity book = data.createBookWithFile(library, "Media Book " + UUID.randomUUID(), BookFileType.PDF, pdfFixture);
        Path cover = writeFixtureCover();

        ResponseEntity<Void> uploadResponse = rest.postForEntity(
                baseUrl() + "/api/v1/books/" + book.getId() + "/metadata/cover/upload",
                buildMultipartEntity(cover, tokens.accessToken()),
                Void.class
        );
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<byte[]> thumbnailResponse = rest.exchange(
                baseUrl() + "/api/v1/media/book/" + book.getId() + "/thumbnail?token=" + tokens.accessToken(),
                HttpMethod.GET,
                null,
                byte[].class
        );
        assertThat(thumbnailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(thumbnailResponse.getBody()).isNotEmpty();

        ResponseEntity<byte[]> coverResponse = rest.exchange(
                baseUrl() + "/api/v1/media/book/" + book.getId() + "/cover?token=" + tokens.accessToken(),
                HttpMethod.GET,
                null,
                byte[].class
        );
        assertThat(coverResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(coverResponse.getBody()).isNotEmpty();
    }

    @Test
    void bookMediaForMissingBookReturnsNotFound() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<byte[]> thumbnailResponse = rest.exchange(
                baseUrl() + "/api/v1/media/book/9999999/thumbnail?token=" + tokens.accessToken(),
                HttpMethod.GET,
                null,
                byte[].class
        );
        assertThat(thumbnailResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<byte[]> coverResponse = rest.exchange(
                baseUrl() + "/api/v1/media/book/9999999/cover?token=" + tokens.accessToken(),
                HttpMethod.GET,
                null,
                byte[].class
        );
        assertThat(coverResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminCanUploadAndRetrieveAuthorPhotoWithQueryToken() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        AuthorEntity author = data.createAuthor("Photo Author " + UUID.randomUUID());
        Path cover = writeFixtureCover();

        ResponseEntity<Void> uploadResponse = rest.postForEntity(
                baseUrl() + "/api/v1/authors/" + author.getId() + "/photo/upload",
                buildMultipartEntity(cover, tokens.accessToken()),
                Void.class
        );
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<byte[]> photoResponse = rest.exchange(
                baseUrl() + "/api/v1/media/author/" + author.getId() + "/photo?token=" + tokens.accessToken(),
                HttpMethod.GET,
                null,
                byte[].class
        );
        assertThat(photoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(photoResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("image/jpeg");
        assertThat(photoResponse.getBody()).isNotEmpty();

        ResponseEntity<byte[]> thumbnailResponse = rest.exchange(
                baseUrl() + "/api/v1/media/author/" + author.getId() + "/thumbnail?token=" + tokens.accessToken(),
                HttpMethod.GET,
                null,
                byte[].class
        );
        assertThat(thumbnailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(thumbnailResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("image/jpeg");
        assertThat(thumbnailResponse.getBody()).isNotEmpty();
    }

    @Test
    void authorPhotoForAuthorWithoutPhotoReturnsNotFound() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        AuthorEntity author = data.createAuthor("No Photo Author " + UUID.randomUUID());

        ResponseEntity<byte[]> photoResponse = rest.exchange(
                baseUrl() + "/api/v1/media/author/" + author.getId() + "/photo?token=" + tokens.accessToken(),
                HttpMethod.GET,
                null,
                byte[].class
        );
        assertThat(photoResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
