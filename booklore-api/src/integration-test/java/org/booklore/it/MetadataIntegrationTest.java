package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.request.IsbnLookupRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.metadata.BookMetadataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class MetadataIntegrationTest extends RestApiIntegrationTest {

    @MockitoBean
    private BookMetadataService bookMetadataService;

    @AfterEach
    void resetMocks() {
        Mockito.reset(bookMetadataService);
    }

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("metadata-it-");
        return data.createLibrary("MetadataITLib " + UUID.randomUUID(), tempDir);
    }

    @Test
    void adminCanGetProspectiveMetadataAsSseStream() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Prospective Metadata Book " + UUID.randomUUID());

        BookMetadata prospective = BookMetadata.builder()
                .title("Mock Prospective Title")
                .authors(List.of("Mock Author"))
                .build();

        when(bookMetadataService.getProspectiveMetadataListForBookId(eq(book.getId()), any(FetchMetadataRequest.class)))
                .thenReturn(Flux.just(prospective));

        FetchMetadataRequest requestBody = FetchMetadataRequest.builder()
                .providers(List.of(MetadataProvider.Google))
                .build();

        HttpHeaders headers = auth.bearerHeaders(tokens.accessToken());
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        HttpEntity<FetchMetadataRequest> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/metadata/prospective",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
        assertThat(response.getBody()).contains("data:");
    }

    @Test
    void adminCanLookupMetadataByIsbn() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        BookMetadata metadata = BookMetadata.builder()
                .title("Mock ISBN Title")
                .isbn13("9781234567890")
                .build();

        when(bookMetadataService.lookupByIsbn(any(IsbnLookupRequest.class))).thenReturn(metadata);

        IsbnLookupRequest request = new IsbnLookupRequest();
        request.setIsbn("9781234567890");

        ResponseEntity<BookMetadata> response = rest.postForEntity(
                baseUrl() + "/api/v1/books/metadata/isbn-lookup",
                auth.bearerEntity(request, tokens.accessToken()),
                BookMetadata.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Mock ISBN Title");
    }

    @Test
    void isbnLookupReturnsNotFoundWhenServiceReturnsNull() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        when(bookMetadataService.lookupByIsbn(any(IsbnLookupRequest.class))).thenReturn(null);

        IsbnLookupRequest request = new IsbnLookupRequest();
        request.setIsbn("0000000000000");

        ResponseEntity<BookMetadata> response = rest.postForEntity(
                baseUrl() + "/api/v1/books/metadata/isbn-lookup",
                auth.bearerEntity(request, tokens.accessToken()),
                BookMetadata.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminCanGetDetailedProviderMetadata() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        BookMetadata metadata = BookMetadata.builder()
                .title("Mock Detailed Title")
                .provider(MetadataProvider.Google)
                .build();

        when(bookMetadataService.getDetailedProviderMetadata(MetadataProvider.Google, "provider-item-123"))
                .thenReturn(metadata);

        ResponseEntity<BookMetadata> response = rest.exchange(
                baseUrl() + "/api/v1/books/metadata/detail/" + MetadataProvider.Google + "/provider-item-123",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                BookMetadata.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Mock Detailed Title");
    }

    @Test
    void detailedProviderMetadataReturnsNotFoundWhenServiceReturnsNull() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        when(bookMetadataService.getDetailedProviderMetadata(MetadataProvider.Google, "missing-item"))
                .thenReturn(null);

        ResponseEntity<BookMetadata> response = rest.exchange(
                baseUrl() + "/api/v1/books/metadata/detail/" + MetadataProvider.Google + "/missing-item",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                BookMetadata.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
