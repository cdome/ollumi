package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.jooq.dto.UserBookProgressRow;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.AuthorRepository;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.CategoryRepository;
import org.booklore.repository.jooq.JooqReadingSessionRepository;
import org.booklore.repository.jooq.JooqUserBookProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UserStatsIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private JooqReadingSessionRepository readingSessionRepository;

    @Autowired
    private JooqUserBookProgressRepository userBookProgressRepository;

    @Autowired
    private BookMetadataRepository bookMetadataRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private BookFileRepository bookFileRepository;

    private static final int CURRENT_YEAR = LocalDate.now(ZoneId.systemDefault()).getYear();
    private static final int CURRENT_MONTH = LocalDate.now(ZoneId.systemDefault()).getMonthValue();
    private static final int CURRENT_WEEK = LocalDate.now(ZoneId.systemDefault()).get(WeekFields.ISO.weekOfYear());

    @Test
    void readingHeatmapReturnsDataForCurrentYear() throws Exception {
        BookLoreUserEntity user = createStatsUser("heatmap-year");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Heatmap Year Book");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 5f, 5f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/heatmap?year=" + CURRENT_YEAR,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0)).containsKey("date");
        assertThat(response.getBody().get(0)).containsKey("count");
    }

    @Test
    void readingMonthlyHeatmapReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("heatmap-month");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Heatmap Month Book");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 5f, 5f);

        String url = UriComponentsBuilder.fromUriString(baseUrl() + "/api/v1/user-stats/reading/heatmap/monthly")
                .queryParam("year", CURRENT_YEAR)
                .queryParam("month", CURRENT_MONTH)
                .toUriString();

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                url,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void readingTimelineReturnsDataForCurrentWeek() throws Exception {
        BookLoreUserEntity user = createStatsUser("timeline");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Timeline Book");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 5f, 5f);

        String url = UriComponentsBuilder.fromUriString(baseUrl() + "/api/v1/user-stats/reading/timeline")
                .queryParam("year", CURRENT_YEAR)
                .queryParam("week", CURRENT_WEEK)
                .toUriString();

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                url,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void readingSpeedReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("speed");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Speed Book");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 10f, 10f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/speed?year=" + CURRENT_YEAR,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void peakReadingHoursReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("peak");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Peak Book");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 5f, 5f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/peak-hours?year=" + CURRENT_YEAR,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0)).containsKey("hourOfDay");
    }

    @Test
    void favoriteReadingDaysReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("favorite");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Favorite Book");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 5f, 5f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/favorite-days?year=" + CURRENT_YEAR,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0)).containsKey("dayName");
    }

    @Test
    void genreStatisticsReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("genre");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Genre Book", "Science Fiction");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 5f, 5f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/genres",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0).get("genre")).asString().startsWith("Science Fiction");
    }

    @Test
    void completionTimelineReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("completion-timeline");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Completion Timeline Book");
        createUserBookProgress(user, book, ReadStatus.READ, Instant.now(), 5);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/completion-timeline?year=" + CURRENT_YEAR,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0)).containsKey("statusBreakdown");
    }

    @Test
    void bookCompletionHeatmapReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("book-completion");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Book Completion Book");
        createUserBookProgress(user, book, ReadStatus.READ, Instant.now(), 4);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/book-completion-heatmap",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0)).containsKey("year");
        assertThat(response.getBody().get(0)).containsKey("month");
    }

    @Test
    void pageTurnerScoresReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("page-turner");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Page Turner Book");
        createUserBookProgress(user, book, ReadStatus.READ, Instant.now(), 5);
        Instant base = Instant.now().minusSeconds(86400);
        createReadingSession(user, book, BookFileType.EPUB, base, 600, 0f, 20f, 20f);
        createReadingSession(user, book, BookFileType.EPUB, base.plusSeconds(7200), 600, 20f, 50f, 30f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/page-turner-scores",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0)).containsKey("gripScore");
    }

    @Test
    void completionRaceReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("completion-race");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Completion Race Book");
        createUserBookProgress(user, book, ReadStatus.READ, Instant.now(), 5);
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 100f, 100f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/completion-race?year=" + CURRENT_YEAR,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void bookDistributionsReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("distributions");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Distributions Book");
        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setUserId(user.getId());
        progress.setBookId(book.getId());
        progress.setReadStatus(ReadStatus.READ);
        progress.setPersonalRating(4);
        progress.setEpubProgressPercent(0.75f);
        userBookProgressRepository.save(progress);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/book-distributions",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("ratingDistribution");
        assertThat(response.getBody()).containsKey("statusDistribution");
        assertThat(response.getBody()).containsKey("progressDistribution");
    }

    @Test
    void readingDatesReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("dates");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Dates Book");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 5f, 5f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/dates",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void sessionScatterReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("scatter");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Scatter Book");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 5f, 5f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/session-scatter?year=" + CURRENT_YEAR,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0)).containsKey("durationMinutes");
    }

    @Test
    void readingStreakReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("streak");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Streak Book");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 5f, 5f);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/streak",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("currentStreak");
        assertThat(response.getBody()).containsKey("longestStreak");
        assertThat(response.getBody()).containsKey("last52Weeks");
    }

    @Test
    void bookTimelineReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("book-timeline");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createBookWithCategory(user, "Book Timeline Book");
        createReadingSession(user, book, BookFileType.EPUB, Instant.now().minusSeconds(3600), 600, 0f, 5f, 5f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/reading/book-timeline?year=" + CURRENT_YEAR,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void listeningMonthlyHeatmapReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("listening-heatmap");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createAudiobook(user, "Listening Heatmap Book");
        createReadingSession(user, book, BookFileType.AUDIOBOOK, Instant.now().minusSeconds(3600), 600, 0f, 10f, 10f);

        String url = UriComponentsBuilder.fromUriString(baseUrl() + "/api/v1/user-stats/listening/heatmap/monthly")
                .queryParam("year", CURRENT_YEAR)
                .queryParam("month", CURRENT_MONTH)
                .toUriString();

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                url,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void weeklyListeningTrendReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("listening-trend");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createAudiobook(user, "Listening Trend Book");
        createReadingSession(user, book, BookFileType.AUDIOBOOK, Instant.now().minusSeconds(3600), 600, 0f, 10f, 10f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/listening/weekly-trend?weeks=26",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void listeningCompletionReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("listening-completion");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createAudiobook(user, "Listening Completion Book");
        createUserBookProgress(user, book, ReadStatus.READ, Instant.now(), 5);
        createReadingSession(user, book, BookFileType.AUDIOBOOK, Instant.now().minusSeconds(3600), 600, 0f, 100f, 100f);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/listening/completion",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalAudiobooks")).isEqualTo(1);
        assertThat(response.getBody().get("completed")).isEqualTo(1);
    }

    @Test
    void monthlyListeningPaceReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("listening-pace");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createAudiobook(user, "Listening Pace Book");
        createUserBookProgress(user, book, ReadStatus.READ, Instant.now(), 5);
        createReadingSession(user, book, BookFileType.AUDIOBOOK, Instant.now().minusSeconds(3600), 600, 0f, 100f, 100f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/listening/monthly-pace?months=12",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0)).containsKey("booksCompleted");
    }

    @Test
    void listeningFinishFunnelReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("listening-funnel");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createAudiobook(user, "Listening Funnel Book");
        createReadingSession(user, book, BookFileType.AUDIOBOOK, Instant.now().minusSeconds(3600), 600, 0f, 100f, 100f);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/listening/finish-funnel",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalStarted")).isEqualTo(1);
        assertThat(response.getBody().get("completed")).isEqualTo(1);
    }

    @Test
    void listeningPeakHoursReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("listening-peak");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createAudiobook(user, "Listening Peak Book");
        createReadingSession(user, book, BookFileType.AUDIOBOOK, Instant.now().minusSeconds(3600), 600, 0f, 10f, 10f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/listening/peak-hours?year=" + CURRENT_YEAR,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void listeningGenreStatisticsReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("listening-genre");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createAudiobook(user, "Listening Genre Book", "Thriller");
        createReadingSession(user, book, BookFileType.AUDIOBOOK, Instant.now().minusSeconds(3600), 600, 0f, 10f, 10f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/listening/genres",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0).get("genre")).asString().startsWith("Thriller");
    }

    @Test
    void listeningAuthorStatisticsReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("listening-author");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createAudiobook(user, "Listening Author Book", "Thriller", "Jane Doe");
        createReadingSession(user, book, BookFileType.AUDIOBOOK, Instant.now().minusSeconds(3600), 600, 0f, 10f, 10f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/listening/authors",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0).get("author")).isEqualTo("Jane Doe");
    }

    @Test
    void listeningSessionScatterReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("listening-scatter");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createAudiobook(user, "Listening Scatter Book");
        createReadingSession(user, book, BookFileType.AUDIOBOOK, Instant.now().minusSeconds(3600), 600, 0f, 10f, 10f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/listening/session-scatter",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void listeningLongestBooksReturnsData() throws Exception {
        BookLoreUserEntity user = createStatsUser("listening-longest");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        BookEntity book = createAudiobook(user, "Listening Longest Book");
        createReadingSession(user, book, BookFileType.AUDIOBOOK, Instant.now().minusSeconds(3600), 600, 0f, 100f, 100f);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/user-stats/listening/longest-books",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    private BookLoreUserEntity createStatsUser(String suffix) {
        return auth.createUser("stats-user-" + suffix + "-" + UUID.randomUUID(), "password", p -> p.setPermissionAccessUserStats(true));
    }

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("user-stats-it-");
        return data.createLibrary("UserStatsLib " + UUID.randomUUID(), tempDir);
    }

    private BookEntity createBookWithCategory(BookLoreUserEntity user, String title) throws Exception {
        return createBookWithCategory(user, title, "Fiction");
    }

    private BookEntity createBookWithCategory(BookLoreUserEntity user, String title, String categoryName) throws Exception {
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, title);
        BookMetadataEntity metadata = book.getMetadata();
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder().name(categoryName + " " + UUID.randomUUID()).build());
        metadata.setCategories(new HashSet<>(Set.of(category)));
        metadata.setPageCount(300);
        bookMetadataRepository.save(metadata);
        return book;
    }

    private BookEntity createBookWithCategory(BookLoreUserEntity user, String title, String categoryName, String authorName) throws Exception {
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, title);
        BookMetadataEntity metadata = book.getMetadata();
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder().name(categoryName + " " + UUID.randomUUID()).build());
        AuthorEntity author = authorRepository.save(AuthorEntity.builder().name(authorName).build());
        metadata.setCategories(new HashSet<>(Set.of(category)));
        metadata.setAuthors(new ArrayList<>(List.of(author)));
        metadata.setPageCount(300);
        bookMetadataRepository.save(metadata);
        return book;
    }

    private BookEntity createAudiobook(BookLoreUserEntity user, String title) throws Exception {
        return createAudiobook(user, title, "Fiction");
    }

    private BookEntity createAudiobook(BookLoreUserEntity user, String title, String categoryName) throws Exception {
        BookEntity book = createBookWithCategory(user, title, categoryName);
        attachAudiobookFile(book);
        return book;
    }

    private BookEntity createAudiobook(BookLoreUserEntity user, String title, String categoryName, String authorName) throws Exception {
        BookEntity book = createBookWithCategory(user, title, categoryName, authorName);
        attachAudiobookFile(book);
        return book;
    }

    private void attachAudiobookFile(BookEntity book) {
        BookFileEntity file = BookFileEntity.builder()
                .book(book)
                .fileName("book.m4b")
                .fileSubPath("")
                .bookType(BookFileType.AUDIOBOOK)
                .isBookFormat(true)
                .isFixedLayout(false)
                .durationSeconds(3600L)
                .build();
        bookFileRepository.save(file);
    }

    private void createReadingSession(BookLoreUserEntity user, BookEntity book, BookFileType bookType,
                                      Instant startTime, int durationSeconds,
                                      float startProgress, float endProgress, float progressDelta) {
        readingSessionRepository.insert(
                user.getId(), book.getId(), bookType,
                startTime, startTime.plusSeconds(durationSeconds), durationSeconds,
                null,
                startProgress, endProgress, progressDelta,
                null, null);
    }

    private void createUserBookProgress(BookLoreUserEntity user, BookEntity book, ReadStatus status,
                                        Instant finishedAt, int rating) {
        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setUserId(user.getId());
        progress.setBookId(book.getId());
        progress.setReadStatus(status);
        progress.setDateFinished(finishedAt);
        progress.setReadStatusModifiedTime(finishedAt);
        progress.setPersonalRating(rating);
        userBookProgressRepository.save(progress);
    }
}
