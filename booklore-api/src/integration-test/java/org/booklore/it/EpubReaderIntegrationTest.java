package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.dto.response.EpubBookInfo;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EpubReaderIntegrationTest extends RestApiIntegrationTest {

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("epub-reader-it-");
        return data.createLibrary("EpubReaderITLib " + UUID.randomUUID(), tempDir);
    }

    @Test
    void adminCanGetEpubInfo() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        Path fixtureDir = Files.createTempDirectory("fixture-");
        Path epubFixture = FixtureFactory.writeEpub(fixtureDir.resolve("book.epub"));
        BookEntity book = data.createBookWithFile(library, "Epub Book " + UUID.randomUUID(), BookFileType.EPUB, epubFixture);

        ResponseEntity<EpubBookInfo> response = rest.exchange(
                baseUrl() + "/api/v1/epub/" + book.getId() + "/info",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                EpubBookInfo.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        EpubBookInfo info = response.getBody();
        assertThat(info).isNotNull();
        assertThat(info.getMetadata()).containsKey("title");
        assertThat(info.getSpine()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(info.getManifest())
                .anyMatch(item -> "OEBPS/chapter.xhtml".equals(item.getHref()));
    }

    @Test
    void adminCanStreamEpubFileWithQueryToken() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        Path fixtureDir = Files.createTempDirectory("fixture-");
        Path epubFixture = FixtureFactory.writeEpub(fixtureDir.resolve("book.epub"));
        BookEntity book = data.createBookWithFile(library, "Epub File Book " + UUID.randomUUID(), BookFileType.EPUB, epubFixture);

        ResponseEntity<byte[]> response = rest.exchange(
                baseUrl() + "/api/v1/epub/" + book.getId() + "/file/OEBPS/chapter.xhtml?token=" + tokens.accessToken(),
                HttpMethod.GET,
                null,
                byte[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/xhtml+xml");
        assertThat(response.getBody()).isNotEmpty();
        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(body).contains("Hello, BookLore reader integration test.");
    }
}
