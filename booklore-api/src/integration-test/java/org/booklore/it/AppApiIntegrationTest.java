package org.booklore.it;

import org.booklore.app.dto.*;
import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.*;
import org.booklore.repository.jooq.JooqAnnotationRepository;
import org.booklore.repository.jooq.JooqBookMarkRepository;
import org.booklore.repository.jooq.JooqBookNoteV2Repository;
import org.booklore.repository.jooq.JooqUserBookProgressRepository;
import org.booklore.repository.jooq.dto.UserBookProgressRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class AppApiIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private JooqAnnotationRepository annotationRepository;

    @Autowired
    private JooqBookNoteV2Repository bookNoteV2Repository;

    @Autowired
    private JooqBookMarkRepository bookMarkRepository;

    @Autowired
    private JooqUserBookProgressRepository userBookProgressRepository;

    private static final ParameterizedTypeReference<AppPageResponse<AppBookSummary>> BOOK_PAGE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<AppBookSummary>> BOOK_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<AppLibrarySummary>> LIBRARY_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<AppShelfSummary>> SHELF_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<AppMagicShelfSummary>> MAGIC_SHELF_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AppPageResponse<AppAuthorSummary>> AUTHOR_PAGE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AppPageResponse<AppSeriesSummary>> SERIES_PAGE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AppPageResponse<AppNotebookBookSummary>> NOTEBOOK_BOOK_PAGE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AppPageResponse<AppNotebookEntry>> NOTEBOOK_ENTRY_PAGE =
            new ParameterizedTypeReference<>() {};

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("app-api-it-");
        return data.createLibrary("AppApiIT " + UUID.randomUUID(), tempDir);
    }

    private BookEntity createPhantomBook(LibraryEntity library, String title) throws Exception {
        BookEntity book = BookEntity.builder()
                .library(library)
                .libraryPath(library.getLibraryPaths().get(0))
                .addedOn(Instant.now())
                .scannedOn(Instant.now())
                .deleted(false)
                .isPhysical(false)
                .build();

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(book)
                .title(title)
                .language("en")
                .build();
        metadata.setAuthors(new ArrayList<>());
        metadata.updateSearchText();
        book.setMetadata(metadata);

        String fileName = sanitize(title) + ".pdf";
        BookFileEntity file = BookFileEntity.builder()
                .book(book)
                .fileName(fileName)
                .fileSubPath("")
                .isBookFormat(true)
                .bookType(BookFileType.PDF)
                .build();
        book.setBookFiles(new ArrayList<>(List.of(file)));

        Path libraryRoot = Path.of(library.getLibraryPaths().get(0).getPath());
        Files.createFile(libraryRoot.resolve(fileName));

        return bookRepository.save(book);
    }

    private String sanitize(String title) {
        return title.replaceAll("[^a-zA-Z0-9\\-]", "_");
    }

    private HttpEntity<Void> auth(String token) {
        return auth.bearerEntity(token);
    }

    private <T> HttpEntity<T> auth(T body, String token) {
        return auth.bearerEntity(body, token);
    }

    private Long createMagicShelf(String token) {
        Map<String, Object> request = Map.of(
                "name", "Magic Shelf " + UUID.randomUUID(),
                "filterJson", "{\"genre\":\"fantasy\"}",
                "isPublic", false
        );
        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/magic-shelves",
                auth(request, token),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private void seedNotebookEntries(BookEntity book) {
        BookLoreUserEntity user = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();

        annotationRepository.insert(
                book.getId(), user.getId(),
                "epubcfi(/6/2[id001]!/4/1:0)",
                "Highlighted text",
                "yellow",
                "solid",
                "annotation note",
                "Chapter One");

        bookNoteV2Repository.insert(
                book.getId(), user.getId(),
                "epubcfi(/6/2[id002]!/4/1:0)",
                "Selected text",
                "This is a note",
                "blue",
                "Chapter Two");

        bookMarkRepository.insert(
                book.getId(), user.getId(),
                null, null, null,
                "Important bookmark",
                1);
    }

    // ---------------------- AppBookController ----------------------

    @Test
    void adminCanListAppBooks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "App Book " + UUID.randomUUID());

        ResponseEntity<AppPageResponse<AppBookSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/books",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                BOOK_PAGE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent())
                .extracting(AppBookSummary::getId)
                .contains(book.getId());
    }

    @Test
    void adminCanFilterAppBooks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Filtered App Book " + UUID.randomUUID());

        String url = UriComponentsBuilder.fromUriString(baseUrl() + "/api/v1/app/books")
                .queryParam("libraryId", library.getId())
                .queryParam("search", "Filtered")
                .toUriString();

        ResponseEntity<AppPageResponse<AppBookSummary>> response = rest.exchange(
                url,
                HttpMethod.GET,
                auth(tokens.accessToken()),
                BOOK_PAGE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent())
                .extracting(AppBookSummary::getId)
                .contains(book.getId());
    }

    @Test
    void adminCanGetAppBookDetail() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "App Detail Book " + UUID.randomUUID());

        ResponseEntity<AppBookDetail> response = rest.exchange(
                baseUrl() + "/api/v1/app/books/" + book.getId(),
                HttpMethod.GET,
                auth(tokens.accessToken()),
                AppBookDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(book.getId());
        assertThat(response.getBody().getTitle()).isEqualTo(book.getMetadata().getTitle());
    }

    @Test
    void adminCanSearchAppBooks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Search App Book " + UUID.randomUUID());

        String url = UriComponentsBuilder.fromUriString(baseUrl() + "/api/v1/app/books/search")
                .queryParam("q", "Search")
                .toUriString();

        ResponseEntity<AppPageResponse<AppBookSummary>> response = rest.exchange(
                url,
                HttpMethod.GET,
                auth(tokens.accessToken()),
                BOOK_PAGE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent())
                .extracting(AppBookSummary::getId)
                .contains(book.getId());
    }

    @Test
    void adminCanGetContinueReading() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Continue Reading Book " + UUID.randomUUID());
        BookLoreUserEntity user = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();

        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setUserId(user.getId());
        progress.setBookId(book.getId());
        progress.setReadStatus(ReadStatus.READING);
        progress.setLastReadTime(Instant.now());
        userBookProgressRepository.save(progress);

        ResponseEntity<List<AppBookSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/books/continue-reading",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                BOOK_LIST
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(AppBookSummary::getId)
                .contains(book.getId());
    }

    @Test
    void adminCanGetContinueListening() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Continue Listening Book " + UUID.randomUUID());
        BookLoreUserEntity user = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();

        UserBookProgressRow progress = new UserBookProgressRow();
        progress.setUserId(user.getId());
        progress.setBookId(book.getId());
        progress.setReadStatus(ReadStatus.READING);
        progress.setLastReadTime(Instant.now());
        userBookProgressRepository.save(progress);

        ResponseEntity<List<AppBookSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/books/continue-listening",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                BOOK_LIST
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void adminCanGetRecentlyAdded() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Recently Added Book " + UUID.randomUUID());

        ResponseEntity<List<AppBookSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/books/recently-added",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                BOOK_LIST
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(AppBookSummary::getId)
                .contains(book.getId());
    }

    @Test
    void adminCanGetRecentlyScanned() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Recently Scanned Book " + UUID.randomUUID());

        ResponseEntity<List<AppBookSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/books/recently-scanned",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                BOOK_LIST
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(AppBookSummary::getId)
                .contains(book.getId());
    }

    @Test
    void adminCanUpdateAppBookStatus() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Status Update Book " + UUID.randomUUID());

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(ReadStatus.READING);

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/app/books/" + book.getId() + "/status",
                HttpMethod.PUT,
                auth(request, tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AppBookDetail> detail = rest.exchange(
                baseUrl() + "/api/v1/app/books/" + book.getId(),
                HttpMethod.GET,
                auth(tokens.accessToken()),
                AppBookDetail.class
        );
        assertThat(detail.getBody().getReadStatus()).isEqualTo("READING");
    }

    @Test
    void adminCanUpdateAppBookRating() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Rating Update Book " + UUID.randomUUID());

        UpdateRatingRequest request = new UpdateRatingRequest();
        request.setRating(4);

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/app/books/" + book.getId() + "/rating",
                HttpMethod.PUT,
                auth(request, tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AppBookDetail> detail = rest.exchange(
                baseUrl() + "/api/v1/app/books/" + book.getId(),
                HttpMethod.GET,
                auth(tokens.accessToken()),
                AppBookDetail.class
        );
        assertThat(detail.getBody().getPersonalRating()).isEqualTo(4);
    }

    @Test
    void adminCanGetRandomAppBooks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        createPhantomBook(library, "Random Book " + UUID.randomUUID());

        ResponseEntity<AppPageResponse<AppBookSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/books/random",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                BOOK_PAGE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).isNotEmpty();
    }

    // ---------------------- AppLibraryController ----------------------

    @Test
    void adminCanListAppLibraries() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        createPhantomBook(library, "Library Book " + UUID.randomUUID());

        ResponseEntity<List<AppLibrarySummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/libraries",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                LIBRARY_LIST
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(AppLibrarySummary::getId)
                .contains(library.getId());
    }

    // ---------------------- AppShelfController ----------------------

    @Test
    void adminCanListAppShelves() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        String shelfName = "My Shelf " + UUID.randomUUID();
        Map<String, Object> createRequest = Map.of(
                "name", shelfName,
                "publicShelf", false
        );
        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/shelves",
                auth(createRequest, tokens.accessToken()),
                Map.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long shelfId = ((Number) createResponse.getBody().get("id")).longValue();

        ResponseEntity<List<AppShelfSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/shelves",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                SHELF_LIST
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(AppShelfSummary::getId)
                .contains(shelfId);
    }

    @Test
    void adminCanListAppMagicShelves() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Long magicShelfId = createMagicShelf(tokens.accessToken());

        ResponseEntity<List<AppMagicShelfSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/shelves/magic",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                MAGIC_SHELF_LIST
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(AppMagicShelfSummary::getId)
                .contains(magicShelfId);
    }

    @Test
    void adminCanGetBooksByMagicShelf() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        Long magicShelfId = createMagicShelf(tokens.accessToken());

        ResponseEntity<AppPageResponse<AppBookSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/shelves/magic/" + magicShelfId + "/books",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                BOOK_PAGE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    // ---------------------- AppAuthorController ----------------------

    @Test
    void adminCanListAppAuthors() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Author Book " + UUID.randomUUID());
        AuthorEntity author = data.createAuthor("App Author " + UUID.randomUUID());
        book.getMetadata().getAuthors().add(author);
        bookRepository.save(book);

        ResponseEntity<AppPageResponse<AppAuthorSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/authors",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                AUTHOR_PAGE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent())
                .extracting(AppAuthorSummary::getId)
                .contains(author.getId());
    }

    @Test
    void adminCanGetAppAuthorDetail() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Author Detail Book " + UUID.randomUUID());
        AuthorEntity author = data.createAuthor("App Author Detail " + UUID.randomUUID());
        book.getMetadata().getAuthors().add(author);
        bookRepository.save(book);

        ResponseEntity<AppAuthorDetail> response = rest.exchange(
                baseUrl() + "/api/v1/app/authors/" + author.getId(),
                HttpMethod.GET,
                auth(tokens.accessToken()),
                AppAuthorDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(author.getId());
        assertThat(response.getBody().getName()).isEqualTo(author.getName());
    }

    // ---------------------- AppSeriesController ----------------------

    @Test
    void adminCanListAppSeries() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Series Book " + UUID.randomUUID());
        book.getMetadata().setSeriesName("App Series " + UUID.randomUUID());
        bookRepository.save(book);

        ResponseEntity<AppPageResponse<AppSeriesSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/series",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                SERIES_PAGE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent())
                .extracting(AppSeriesSummary::getSeriesName)
                .contains(book.getMetadata().getSeriesName());
    }

    @Test
    void adminCanGetAppSeriesBooks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        String seriesName = "App Series Books " + UUID.randomUUID();
        BookEntity book = createPhantomBook(library, "Series Book One " + UUID.randomUUID());
        book.getMetadata().setSeriesName(seriesName);
        bookRepository.save(book);

        String url = UriComponentsBuilder.fromUriString(baseUrl() + "/api/v1/app/series/{seriesName}/books")
                .buildAndExpand(Map.of("seriesName", seriesName))
                .toUriString();

        ResponseEntity<AppPageResponse<AppBookSummary>> response = rest.exchange(
                url,
                HttpMethod.GET,
                auth(tokens.accessToken()),
                BOOK_PAGE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent())
                .extracting(AppBookSummary::getId)
                .contains(book.getId());
    }

    // ---------------------- AppNotebookController ----------------------

    @Test
    void adminCanListNotebookBooks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Notebook Book " + UUID.randomUUID());
        seedNotebookEntries(book);

        ResponseEntity<AppPageResponse<AppNotebookBookSummary>> response = rest.exchange(
                baseUrl() + "/api/v1/app/notebook/books",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                NOTEBOOK_BOOK_PAGE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent())
                .extracting(AppNotebookBookSummary::getBookId)
                .contains(book.getId());
    }

    @Test
    void adminCanListNotebookEntriesForBook() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Notebook Entries Book " + UUID.randomUUID());
        seedNotebookEntries(book);

        ResponseEntity<AppPageResponse<AppNotebookEntry>> response = rest.exchange(
                baseUrl() + "/api/v1/app/notebook/books/" + book.getId() + "/entries",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                NOTEBOOK_ENTRY_PAGE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(3);
        assertThat(response.getBody().getContent())
                .extracting(AppNotebookEntry::getType)
                .containsExactlyInAnyOrder("HIGHLIGHT", "NOTE", "BOOKMARK");
    }

    @Test
    void adminCanUpdateNotebookEntry() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Update Notebook Book " + UUID.randomUUID());
        seedNotebookEntries(book);

        AppNotebookEntry entry = rest.exchange(
                baseUrl() + "/api/v1/app/notebook/books/" + book.getId() + "/entries",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                NOTEBOOK_ENTRY_PAGE
        ).getBody().getContent().stream()
                .filter(e -> "HIGHLIGHT".equals(e.getType()))
                .findFirst()
                .orElseThrow();

        AppNotebookUpdateRequest request = new AppNotebookUpdateRequest();
        request.setNote("Updated note text");
        request.setColor("#FF5733");

        ResponseEntity<AppNotebookEntry> response = rest.exchange(
                baseUrl() + "/api/v1/app/notebook/entries/" + entry.getId() + "?type=HIGHLIGHT",
                HttpMethod.PUT,
                auth(request, tokens.accessToken()),
                AppNotebookEntry.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getNote()).isEqualTo("Updated note text");
        assertThat(response.getBody().getColor()).isEqualTo("#FF5733");
    }

    @Test
    void adminCanDeleteNotebookEntry() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Delete Notebook Book " + UUID.randomUUID());
        seedNotebookEntries(book);

        AppNotebookEntry entry = rest.exchange(
                baseUrl() + "/api/v1/app/notebook/books/" + book.getId() + "/entries",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                NOTEBOOK_ENTRY_PAGE
        ).getBody().getContent().stream()
                .filter(e -> "BOOKMARK".equals(e.getType()))
                .findFirst()
                .orElseThrow();

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/app/notebook/entries/" + entry.getId() + "?type=BOOKMARK",
                HttpMethod.DELETE,
                auth(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<AppPageResponse<AppNotebookEntry>> after = rest.exchange(
                baseUrl() + "/api/v1/app/notebook/books/" + book.getId() + "/entries",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                NOTEBOOK_ENTRY_PAGE
        );
        assertThat(after.getBody().getContent())
                .extracting(AppNotebookEntry::getType)
                .doesNotContain("BOOKMARK");
    }

    // ---------------------- AppFilterController ----------------------

    @Test
    void adminCanGetFilterOptions() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Filter Book " + UUID.randomUUID());
        AuthorEntity author = data.createAuthor("Filter Author " + UUID.randomUUID());
        book.getMetadata().getAuthors().add(author);
        bookRepository.save(book);

        ResponseEntity<AppFilterOptions> response = rest.exchange(
                baseUrl() + "/api/v1/app/filter-options?libraryId=" + library.getId(),
                HttpMethod.GET,
                auth(tokens.accessToken()),
                AppFilterOptions.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAuthors())
                .extracting(AppFilterOptions.AuthorOption::getName)
                .contains(author.getName());
    }

    // ---------------------- AppUserController ----------------------

    @Test
    void adminCanGetCurrentUser() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<AppUserInfo> response = rest.exchange(
                baseUrl() + "/api/v1/app/users/me",
                HttpMethod.GET,
                auth(tokens.accessToken()),
                AppUserInfo.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAdmin()).isTrue();
        assertThat(response.getBody().getMaxFileUploadSizeMb()).isGreaterThanOrEqualTo(0);
    }
}
