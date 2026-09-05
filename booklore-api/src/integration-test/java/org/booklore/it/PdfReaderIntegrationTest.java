package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.dto.response.PdfBookInfo;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class PdfReaderIntegrationTest extends RestApiIntegrationTest {

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("pdf-reader-it-");
        return data.createLibrary("PdfReaderITLib " + UUID.randomUUID(), tempDir);
    }

    @Test
    void adminCanGetPdfInfo() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        Path fixtureDir = Files.createTempDirectory("fixture-");
        Path pdfFixture = FixtureFactory.writePdf(fixtureDir.resolve("book.pdf"));
        BookEntity book = data.createBookWithFile(library, "Pdf Book " + UUID.randomUUID(), BookFileType.PDF, pdfFixture);

        ResponseEntity<PdfBookInfo> response = rest.exchange(
                baseUrl() + "/api/v1/pdf/" + book.getId() + "/info",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                PdfBookInfo.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PdfBookInfo info = response.getBody();
        assertThat(info).isNotNull();
        assertThat(info.getPageCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void adminCanListPdfPages() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        Path fixtureDir = Files.createTempDirectory("fixture-");
        Path pdfFixture = FixtureFactory.writePdf(fixtureDir.resolve("book.pdf"));
        BookEntity book = data.createBookWithFile(library, "Pdf Pages Book " + UUID.randomUUID(), BookFileType.PDF, pdfFixture);

        ResponseEntity<List<Integer>> response = rest.exchange(
                baseUrl() + "/api/v1/pdf/" + book.getId() + "/pages",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(1);
    }
}
