package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ReadingSessionIntegrationTest extends RestApiIntegrationTest {

    @Test
    void canRecordReadingSessionAndRetrieveIt() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Path tempDir = Files.createTempDirectory("reading-session-it-");
        LibraryEntity library = data.createLibrary("ReadingSessionLib " + UUID.randomUUID(), tempDir);
        BookEntity book = data.createBook(library, "Test Book");

        Instant startTime = Instant.parse("2023-06-15T10:00:00Z");
        Instant endTime = startTime.plusSeconds(600);

        ReadingSessionRequest request = new ReadingSessionRequest();
        request.setBookId(book.getId());
        request.setBookType(BookFileType.EPUB);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setDurationSeconds(600);
        request.setDurationFormatted("10m");
        request.setStartProgress(0.0f);
        request.setEndProgress(10.0f);
        request.setProgressDelta(10.0f);
        request.setStartLocation("loc-1");
        request.setEndLocation("loc-2");

        ResponseEntity<Void> postResponse = rest.exchange(
                baseUrl() + "/api/v1/reading-sessions",
                HttpMethod.POST,
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/reading-sessions/book/" + book.getId() + "?page=0&size=5",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsKey("content");
        List<Map<String, Object>> content = (List<Map<String, Object>>) getResponse.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("bookId")).isEqualTo(book.getId().intValue());
        assertThat(content.get(0).get("bookTitle")).isEqualTo("Test Book");
    }

    @Test
    void getReadingSessionsForUnknownBookReturnsNotFound() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/reading-sessions/book/999999?page=0&size=5",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
