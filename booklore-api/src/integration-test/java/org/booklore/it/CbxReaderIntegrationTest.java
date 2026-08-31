package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.dto.response.CbxPageInfo;
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

public class CbxReaderIntegrationTest extends RestApiIntegrationTest {

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("cbx-reader-it-");
        return data.createLibrary("CbxReaderITLib " + UUID.randomUUID(), tempDir);
    }

    @Test
    void adminCanListCbxPages() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        Path fixtureDir = Files.createTempDirectory("fixture-");
        Path cbzFixture = FixtureFactory.writeCbz(fixtureDir.resolve("book.cbz"));
        BookEntity book = data.createBookWithFile(library, "Cbz Book " + UUID.randomUUID(), BookFileType.CBX, cbzFixture);

        ResponseEntity<List<Integer>> response = rest.exchange(
                baseUrl() + "/api/v1/cbx/" + book.getId() + "/pages",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(1);
    }

    @Test
    void adminCanGetCbxPageInfo() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        Path fixtureDir = Files.createTempDirectory("fixture-");
        Path cbzFixture = FixtureFactory.writeCbz(fixtureDir.resolve("book.cbz"));
        BookEntity book = data.createBookWithFile(library, "Cbz Page Info Book " + UUID.randomUUID(), BookFileType.CBX, cbzFixture);

        ResponseEntity<List<CbxPageInfo>> response = rest.exchange(
                baseUrl() + "/api/v1/cbx/" + book.getId() + "/page-info",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getPageNumber()).isEqualTo(1);
        assertThat(response.getBody().get(0).getDisplayName()).isEqualTo("page1");
    }
}
