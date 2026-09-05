package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.dto.AuthorDetails;
import org.booklore.model.dto.AuthorSearchResult;
import org.booklore.model.dto.AuthorSummary;
import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.request.AuthorMatchRequest;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.enums.AuthorMetadataSource;
import org.booklore.service.AuthorMetadataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AuthorIntegrationTest extends RestApiIntegrationTest {

    @MockitoBean
    private AuthorMetadataService authorMetadataService;

    @AfterEach
    void resetMock() {
        reset(authorMetadataService);
    }

    @Test
    void adminCanSearchAuthorMetadataByQuery() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        AuthorEntity author = data.createAuthor("TestAuthor" + UUID.randomUUID());

        AuthorSearchResult result = AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS)
                .asin("B123456789")
                .name(author.getName())
                .description("A test author")
                .imageUrl("http://example.com/photo.jpg")
                .build();
        when(authorMetadataService.searchAuthorMetadata(author.getName(), "us")).thenReturn(List.of(result));

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/authors/" + author.getId() + "/search-metadata?q=" + author.getName(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("asin")).isEqualTo("B123456789");
    }

    @Test
    void adminCanLookupAuthorMetadataByAsin() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        AuthorEntity author = data.createAuthor("TestAuthor" + UUID.randomUUID());

        AuthorSearchResult result = AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS)
                .asin("B123456789")
                .name(author.getName())
                .build();
        when(authorMetadataService.lookupAuthorByAsin("B123456789", "us")).thenReturn(List.of(result));

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/authors/" + author.getId() + "/search-metadata?asin=B123456789",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("asin")).isEqualTo("B123456789");
    }

    @Test
    void adminCanMatchAuthor() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        AuthorEntity author = data.createAuthor("TestAuthor" + UUID.randomUUID());

        AuthorDetails details = AuthorDetails.builder()
                .id(author.getId())
                .name(author.getName())
                .description("Matched author")
                .asin("B123456789")
                .build();
        when(authorMetadataService.matchAuthor(eq(author.getId()), any(AuthorMatchRequest.class))).thenReturn(details);

        AuthorMatchRequest request = new AuthorMatchRequest();
        request.setSource(AuthorMetadataSource.AUDNEXUS);
        request.setAsin("B123456789");
        request.setRegion("us");

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                baseUrl() + "/api/v1/authors/" + author.getId() + "/match",
                HttpMethod.POST,
                auth.bearerEntity(request, tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("asin", "B123456789");
        assertThat(response.getBody()).containsEntry("description", "Matched author");
    }

    @Test
    void adminCanQuickMatchAuthor() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        AuthorEntity author = data.createAuthor("TestAuthor" + UUID.randomUUID());

        AuthorDetails details = AuthorDetails.builder()
                .id(author.getId())
                .name(author.getName())
                .description("Quick matched author")
                .asin("B123456789")
                .build();
        when(authorMetadataService.quickMatchAuthor(author.getId(), "us")).thenReturn(details);

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                baseUrl() + "/api/v1/authors/" + author.getId() + "/quick-match?region=us",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("asin", "B123456789");
        assertThat(response.getBody()).containsEntry("description", "Quick matched author");
    }

    @Test
    void adminCanAutoMatchAuthorsWithSse() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        AuthorEntity author = data.createAuthor("TestAuthor" + UUID.randomUUID());

        AuthorSummary summary = AuthorSummary.builder()
                .id(author.getId())
                .name(author.getName())
                .asin("B123456789")
                .bookCount(0)
                .hasPhoto(false)
                .build();
        when(authorMetadataService.autoMatchAuthors(anyList())).thenReturn(Flux.just(summary));

        HttpHeaders headers = auth.bearerHeaders(tokens.accessToken());
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        HttpEntity<List<Long>> entity = new HttpEntity<>(List.of(author.getId()), headers);

        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/api/v1/authors/auto-match",
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
    void adminCanSearchAuthorPhotos() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        AuthorEntity author = data.createAuthor("TestAuthor" + UUID.randomUUID());

        CoverImage image = new CoverImage("http://example.com/photo.jpg", 200, 300, 0);
        when(authorMetadataService.searchAuthorPhotos(author.getName())).thenReturn(List.of(image));

        ResponseEntity<List<CoverImage>> response = rest.exchange(
                baseUrl() + "/api/v1/authors/" + author.getId() + "/search-photos?q=" + author.getName(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getUrl()).isEqualTo("http://example.com/photo.jpg");
    }

    @Test
    void adminCanUploadAuthorPhotoFromUrl() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        AuthorEntity author = data.createAuthor("TestAuthor" + UUID.randomUUID());

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/authors/" + author.getId() + "/photo/url?url=http://example.com/photo.jpg",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authorMetadataService).uploadAuthorPhotoFromUrl(author.getId(), "http://example.com/photo.jpg");
    }
}
