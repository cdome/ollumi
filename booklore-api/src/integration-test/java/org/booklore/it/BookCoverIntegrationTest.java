package org.booklore.it;

import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.request.CoverFetchRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.it.util.AuthTestHelper;
import org.booklore.service.metadata.BookCoverService;
import org.booklore.service.metadata.DuckDuckGoCoverService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BookCoverIntegrationTest extends RestApiIntegrationTest {

    @MockitoBean
    private DuckDuckGoCoverService duckDuckGoCoverService;

    @MockitoBean
    private BookCoverService bookCoverService;

    @AfterEach
    void resetMocks() {
        reset(duckDuckGoCoverService, bookCoverService);
    }

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("book-cover-it-");
        return data.createLibrary("BookCoverITLib " + UUID.randomUUID(), tempDir);
    }

    @Test
    void adminCanFetchCoverImagesForBook() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Cover Search Book " + UUID.randomUUID());

        CoverImage coverImage = new CoverImage("http://example.com/cover.jpg", 400, 600, 1);
        when(duckDuckGoCoverService.getCovers(org.mockito.ArgumentMatchers.any(CoverFetchRequest.class)))
                .thenReturn(List.of(coverImage));

        CoverFetchRequest request = CoverFetchRequest.builder()
                .isbn("1234567890")
                .title(book.getMetadata().getTitle())
                .author("Test Author")
                .coverType("ebook")
                .build();

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/metadata/covers",
                HttpMethod.POST,
                auth.bearerEntity(request, tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("url")).isEqualTo("http://example.com/cover.jpg");
        verify(duckDuckGoCoverService).getCovers(org.mockito.ArgumentMatchers.any(CoverFetchRequest.class));
    }

    @Test
    void adminCanUpdateCoverFromUrl() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Cover URL Book " + UUID.randomUUID());

        Map<String, String> request = Map.of("url", "http://example.com/cover.jpg");
        HttpEntity<Map<String, String>> entity = auth.bearerEntity(request, tokens.accessToken());

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/metadata/cover/from-url",
                HttpMethod.POST,
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(bookCoverService).updateCoverFromUrl(book.getId(), "http://example.com/cover.jpg");
    }
}
