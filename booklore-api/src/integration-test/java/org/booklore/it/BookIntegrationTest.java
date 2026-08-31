package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookViewerSettings;
import org.booklore.model.dto.NewPdfViewerPreferences;
import org.booklore.model.dto.PdfViewerPreferences;
import org.booklore.model.dto.request.AttachBookFileRequest;
import org.booklore.model.dto.request.CreatePhysicalBookRequest;
import org.booklore.model.dto.request.DuplicateDetectionRequest;
import org.booklore.model.dto.request.PersonalRatingUpdateRequest;
import org.booklore.model.dto.request.ReadProgressRequest;
import org.booklore.model.dto.request.BookFileProgress;
import org.booklore.model.dto.request.ReadStatusUpdateRequest;
import org.booklore.model.dto.request.ShelvesAssignmentRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.NewPdfBackgroundColor;
import org.booklore.model.enums.NewPdfPageFitMode;
import org.booklore.model.enums.NewPdfPageScrollMode;
import org.booklore.model.enums.NewPdfPageSpread;
import org.booklore.model.enums.NewPdfPageViewMode;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class BookIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookFileRepository bookFileRepository;

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("book-it-");
        return data.createLibrary("BookITLib " + UUID.randomUUID(), tempDir);
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
        book.setMetadata(metadata);

        String fileName = sanitizeFileName(title) + ".pdf";
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

    private Integer createShelf(String accessToken, String name) {
        Map<String, Object> request = Map.of(
                "name", name,
                "publicShelf", false
        );
        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/shelves",
                auth.bearerEntity(request, accessToken),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        return (Integer) response.getBody().get("id");
    }

    private String sanitizeFileName(String title) {
        return title.replaceAll("[^a-zA-Z0-9\\-]", "_");
    }

    @Test
    void adminCanListBooks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        var book = data.createBook(library, "List Book " + UUID.randomUUID());

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/books",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting("id").contains(book.getId().intValue());
    }

    @Test
    void regularUserCanListBooksFromAssignedLibrary() throws Exception {
        BookLoreUserEntity user = auth.createUser("book-list-user", "password");
        LibraryEntity library = createLibrary();
        data.assignLibraryToUser(user, library);
        var book = data.createBook(library, "Assigned Book " + UUID.randomUUID());
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/books",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting("id").contains(book.getId().intValue());
    }

    @Test
    void adminCanGetBookById() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Get Book " + UUID.randomUUID());

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(book.getId().intValue());
        Map<String, Object> metadata = (Map<String, Object>) response.getBody().get("metadata");
        assertThat(metadata.get("title")).isEqualTo(book.getMetadata().getTitle());
    }

    @Test
    void regularUserCannotGetBookFromUnassignedLibrary() throws Exception {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Secured Book " + UUID.randomUUID());

        BookLoreUserEntity user = auth.createUser("book-sec-user", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanCreatePhysicalBook() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();

        CreatePhysicalBookRequest request = CreatePhysicalBookRequest.builder()
                .libraryId(library.getId())
                .title("Physical Book " + UUID.randomUUID())
                .authors(List.of("Test Author"))
                .language("en")
                .build();

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/books/physical",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> metadata = (Map<String, Object>) response.getBody().get("metadata");
        assertThat(metadata.get("title")).isEqualTo(request.getTitle());
        assertThat(response.getBody().get("isPhysical")).isEqualTo(true);
    }

    @Test
    void regularUserCannotCreatePhysicalBook() throws Exception {
        BookLoreUserEntity user = auth.createUser("book-physical-denied", "password");
        LibraryEntity library = createLibrary();
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        CreatePhysicalBookRequest request = CreatePhysicalBookRequest.builder()
                .libraryId(library.getId())
                .title("Denied Physical Book " + UUID.randomUUID())
                .build();

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/books/physical",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanDeleteBook() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Delete Book " + UUID.randomUUID());

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books?ids=" + book.getId(),
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void regularUserCannotDeleteBook() throws Exception {
        BookLoreUserEntity user = auth.createUser("book-delete-denied", "password");
        LibraryEntity library = createLibrary();
        data.assignLibraryToUser(user, library);
        BookEntity book = createPhantomBook(library, "Protected Delete Book " + UUID.randomUUID());
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books?ids=" + book.getId(),
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanGetBooksByIds() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book1 = createPhantomBook(library, "Batch Book One " + UUID.randomUUID());
        BookEntity book2 = createPhantomBook(library, "Batch Book Two " + UUID.randomUUID());

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/books/batch?ids=" + book1.getId() + "," + book2.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).extracting("id").contains(
                book1.getId().intValue(),
                book2.getId().intValue()
        );
    }

    @Test
    void adminCanGetAndUpdateViewerSettings() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Viewer Book " + UUID.randomUUID());
        Long bookFileId = book.getBookFiles().get(0).getId();

        ResponseEntity<BookViewerSettings> getResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/viewer-setting?bookFileId=" + bookFileId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                BookViewerSettings.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        BookViewerSettings settings = BookViewerSettings.builder()
                .pdfSettings(PdfViewerPreferences.builder()
                        .bookId(book.getId())
                        .zoom("1.5")
                        .spread("auto")
                        .build())
                .newPdfSettings(NewPdfViewerPreferences.builder()
                        .bookId(book.getId())
                        .pageSpread(NewPdfPageSpread.EVEN)
                        .pageViewMode(NewPdfPageViewMode.TWO_PAGE)
                        .fitMode(NewPdfPageFitMode.FIT_PAGE)
                        .scrollMode(NewPdfPageScrollMode.PAGINATED)
                        .backgroundColor(NewPdfBackgroundColor.WHITE)
                        .build())
                .build();

        ResponseEntity<Void> putResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/viewer-setting",
                HttpMethod.PUT,
                auth.bearerEntity(settings, tokens.accessToken()),
                Void.class
        );
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<BookViewerSettings> getAfterResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/viewer-setting?bookFileId=" + bookFileId,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                BookViewerSettings.class
        );
        assertThat(getAfterResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAfterResponse.getBody().getPdfSettings().getZoom()).isEqualTo("1.5");
    }

    @Test
    void adminCanAssignAndUnassignShelves() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Shelf Book " + UUID.randomUUID());
        Integer shelfId = createShelf(tokens.accessToken(), "Assign Shelf " + UUID.randomUUID());

        ShelvesAssignmentRequest assignRequest = new ShelvesAssignmentRequest();
        assignRequest.setBookIds(Set.of(book.getId()));
        assignRequest.setShelvesToAssign(Set.of(shelfId.longValue()));
        assignRequest.setShelvesToUnassign(Set.of());

        ResponseEntity<List<Map<String, Object>>> assignResponse = rest.exchange(
                baseUrl() + "/api/v1/books/shelves",
                HttpMethod.POST,
                auth.bearerEntity(assignRequest, tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> shelves = (List<Map<String, Object>>) getResponse.getBody().get("shelves");
        assertThat(shelves).extracting("id").contains(shelfId);

        ShelvesAssignmentRequest unassignRequest = new ShelvesAssignmentRequest();
        unassignRequest.setBookIds(Set.of(book.getId()));
        unassignRequest.setShelvesToAssign(Set.of());
        unassignRequest.setShelvesToUnassign(Set.of(shelfId.longValue()));

        ResponseEntity<List<Map<String, Object>>> unassignResponse = rest.exchange(
                baseUrl() + "/api/v1/books/shelves",
                HttpMethod.POST,
                auth.bearerEntity(unassignRequest, tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(unassignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminCanUpdateReadProgressAndStatus() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Progress Book " + UUID.randomUUID());
        Long bookFileId = book.getBookFiles().get(0).getId();

        ReadProgressRequest progressRequest = new ReadProgressRequest();
        progressRequest.setBookId(book.getId());
        progressRequest.setFileProgress(new BookFileProgress(bookFileId, null, null, 75.0f, null));

        ResponseEntity<Void> progressResponse = rest.exchange(
                baseUrl() + "/api/v1/books/progress",
                HttpMethod.POST,
                auth.bearerEntity(progressRequest, tokens.accessToken()),
                Void.class
        );
        assertThat(progressResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ReadStatusUpdateRequest statusRequest = new ReadStatusUpdateRequest();
        statusRequest.setBookIds(List.of(book.getId()));
        statusRequest.setStatus("READING");

        ResponseEntity<List<Map<String, Object>>> statusResponse = rest.exchange(
                baseUrl() + "/api/v1/books/status",
                HttpMethod.POST,
                auth.bearerEntity(statusRequest, tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody()).hasSize(1);
        assertThat(statusResponse.getBody().get(0).get("readStatus")).isEqualTo("READING");
    }

    @Test
    void adminCanResetReadProgressAndPersonalRating() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Reset Book " + UUID.randomUUID());

        ReadStatusUpdateRequest statusRequest = new ReadStatusUpdateRequest();
        statusRequest.setBookIds(List.of(book.getId()));
        statusRequest.setStatus("READ");
        rest.exchange(
                baseUrl() + "/api/v1/books/status",
                HttpMethod.POST,
                auth.bearerEntity(statusRequest, tokens.accessToken()),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        ResponseEntity<List<Map<String, Object>>> resetResponse = rest.exchange(
                baseUrl() + "/api/v1/books/reset-progress?type=BOOKLORE",
                HttpMethod.POST,
                auth.bearerEntity(List.of(book.getId()), tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resetResponse.getBody()).hasSize(1);

        PersonalRatingUpdateRequest ratingRequest = new PersonalRatingUpdateRequest(List.of(book.getId()), 5);
        ResponseEntity<List<Map<String, Object>>> ratingResponse = rest.exchange(
                baseUrl() + "/api/v1/books/personal-rating",
                HttpMethod.PUT,
                auth.bearerEntity(ratingRequest, tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(ratingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ratingResponse.getBody().get(0).get("personalRating")).isEqualTo(5);

        ResponseEntity<List<Map<String, Object>>> resetRatingResponse = rest.exchange(
                baseUrl() + "/api/v1/books/reset-personal-rating",
                HttpMethod.POST,
                auth.bearerEntity(List.of(book.getId()), tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resetRatingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resetRatingResponse.getBody().get(0).get("personalRating")).isNull();
    }

    @Test
    void resetProgressWithEmptyListReturnsBadRequest() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books/reset-progress?type=BOOKLORE",
                HttpMethod.POST,
                auth.bearerEntity(List.of(), tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminCanDetectDuplicatesByTitleAndAuthor() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        String title = "Duplicate Title " + UUID.randomUUID();
        String author = "Duplicate Author " + UUID.randomUUID();

        CreatePhysicalBookRequest request1 = CreatePhysicalBookRequest.builder()
                .libraryId(library.getId())
                .title(title)
                .authors(List.of(author))
                .language("en")
                .build();
        CreatePhysicalBookRequest request2 = CreatePhysicalBookRequest.builder()
                .libraryId(library.getId())
                .title(title)
                .authors(List.of(author))
                .language("en")
                .build();

        rest.postForEntity(
                baseUrl() + "/api/v1/books/physical",
                auth.bearerEntity(request1, tokens.accessToken()),
                Map.class
        );
        rest.postForEntity(
                baseUrl() + "/api/v1/books/physical",
                auth.bearerEntity(request2, tokens.accessToken()),
                Map.class
        );

        DuplicateDetectionRequest request = new DuplicateDetectionRequest(
                library.getId(), false, false, true, false, false
        );

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/books/duplicates",
                HttpMethod.POST,
                auth.bearerEntity(request, tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(response.getBody().get(0).get("matchReason")).isEqualTo("TITLE_AUTHOR");
    }

    @Test
    void adminCanTogglePhysicalFlag() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Physical Toggle Book " + UUID.randomUUID());

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/physical?physical=true",
                HttpMethod.PATCH,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("isPhysical")).isEqualTo(true);
    }

    @Test
    void adminCanAttachBookFiles() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();

        BookEntity target = createPhantomBook(library, "Attach Target " + UUID.randomUUID());
        BookEntity source1 = createPhantomBook(library, "Attach Source One " + UUID.randomUUID());
        BookEntity source2 = createPhantomBook(library, "Attach Source Two " + UUID.randomUUID());

        AttachBookFileRequest request = new AttachBookFileRequest();
        request.setSourceBookIds(List.of(source1.getId(), source2.getId()));
        request.setMoveFiles(false);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + target.getId() + "/attach-file",
                HttpMethod.POST,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Integer> deletedIds = (List<Integer>) response.getBody().get("deletedSourceBookIds");
        assertThat(deletedIds).contains(source1.getId().intValue(), source2.getId().intValue());
    }

    @Test
    void adminCanGetRecommendations() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book1 = createPhantomBook(library, "Rec Book One " + UUID.randomUUID());
        createPhantomBook(library, "Rec Book Two " + UUID.randomUUID());

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book1.getId() + "/recommendations?limit=5",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getNonExistentBookReturnsNotFound() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books/9999999",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
