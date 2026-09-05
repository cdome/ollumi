package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.dto.BookReview;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.book.BookReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class BookReviewIntegrationTest extends RestApiIntegrationTest {

    @MockitoBean
    private BookReviewService bookReviewService;

    @AfterEach
    void resetMock() {
        reset(bookReviewService);
    }

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("book-review-it-");
        return data.createLibrary("BookReviewITLib " + UUID.randomUUID(), tempDir);
    }

    @Test
    void adminCanRefreshBookReviews() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Review Book " + UUID.randomUUID());

        BookReview expectedReview = BookReview.builder()
                .id(1L)
                .metadataProvider(MetadataProvider.GoodReads)
                .reviewerName("Reviewer One")
                .title("Great book")
                .rating(4.5f)
                .date(Instant.now())
                .body("Loved it")
                .country("US")
                .spoiler(false)
                .followersCount(10)
                .textReviewsCount(5)
                .build();

        when(bookReviewService.refreshReviews(book.getId())).thenReturn(List.of(expectedReview));

        ResponseEntity<List<BookReview>> response = rest.exchange(
                baseUrl() + "/api/v1/reviews/book/" + book.getId() + "/refresh",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        BookReview actual = response.getBody().get(0);
        assertThat(actual.getReviewerName()).isEqualTo(expectedReview.getReviewerName());
        assertThat(actual.getTitle()).isEqualTo(expectedReview.getTitle());
        assertThat(actual.getRating()).isEqualTo(expectedReview.getRating());
        assertThat(actual.getBody()).isEqualTo(expectedReview.getBody());
        assertThat(actual.getMetadataProvider()).isEqualTo(expectedReview.getMetadataProvider());
    }
}
