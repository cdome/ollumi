package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.BulkMetadataUpdateRequest;
import org.booklore.model.dto.request.DeleteMetadataRequest;
import org.booklore.model.dto.request.MergeMetadataRequest;
import org.booklore.model.dto.request.ToggleAllLockRequest;
import org.booklore.model.dto.request.ToggleFieldLocksRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.Lock;
import org.booklore.model.enums.MergeMetadataType;
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

public class BookMetadataWriteIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("metadata-it-");
        return data.createLibrary("MetadataWriteLib " + UUID.randomUUID(), tempDir);
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

        String fileName = title.replaceAll("[^a-zA-Z0-9\\-]", "_") + ".pdf";
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

    @Test
    void adminCanUpdateBookMetadata() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Update Metadata Book " + UUID.randomUUID());

        String newTitle = "Updated Title " + UUID.randomUUID();
        BookMetadata updated = BookMetadata.builder()
                .title(newTitle)
                .build();
        MetadataUpdateWrapper request = MetadataUpdateWrapper.builder()
                .metadata(updated)
                .build();

        ResponseEntity<BookMetadata> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/metadata",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                BookMetadata.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTitle()).isEqualTo(newTitle);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> metadata = (Map<String, Object>) getResponse.getBody().get("metadata");
        assertThat(metadata.get("title")).isEqualTo(newTitle);
    }

    @Test
    void regularUserCannotUpdateMetadata() throws Exception {
        BookLoreUserEntity user = auth.createUser("metadata-edit-denied", "password");
        LibraryEntity library = createLibrary();
        data.assignLibraryToUser(user, library);
        BookEntity book = createPhantomBook(library, "Protected Metadata Book " + UUID.randomUUID());
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        MetadataUpdateWrapper request = MetadataUpdateWrapper.builder()
                .metadata(BookMetadata.builder().title("T").build())
                .build();

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId() + "/metadata",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanBulkEditMetadata() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book1 = createPhantomBook(library, "Bulk One " + UUID.randomUUID());
        BookEntity book2 = createPhantomBook(library, "Bulk Two " + UUID.randomUUID());

        BulkMetadataUpdateRequest request = new BulkMetadataUpdateRequest();
        request.setBookIds(Set.of(book1.getId(), book2.getId()));
        request.setPublisher("Bulk Publisher " + UUID.randomUUID());
        request.setGenres(Set.of("Bulk Genre " + UUID.randomUUID()));

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/books/bulk-edit-metadata",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book1.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> metadata = (Map<String, Object>) getResponse.getBody().get("metadata");
        assertThat(metadata.get("publisher")).isEqualTo(request.getPublisher());
        assertThat(metadata.get("categories")).asList().hasSize(1);
    }

    @Test
    void adminCanToggleAllMetadataLocks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book1 = createPhantomBook(library, "Lock One " + UUID.randomUUID());
        BookEntity book2 = createPhantomBook(library, "Lock Two " + UUID.randomUUID());

        ToggleAllLockRequest request = new ToggleAllLockRequest();
        request.setBookIds(Set.of(book1.getId(), book2.getId()));
        request.setLock(Lock.LOCK);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/books/metadata/toggle-all-lock",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).extracting("titleLocked").containsOnly(true);
    }

    @Test
    void adminCanToggleFieldLocks() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Field Lock Book " + UUID.randomUUID());

        ToggleFieldLocksRequest request = new ToggleFieldLocksRequest();
        request.setBookIds(List.of(book.getId()));
        request.setFieldActions(Map.of("titleLocked", "LOCK"));

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/books/metadata/toggle-field-locks",
                HttpMethod.PUT,
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> metadata = (Map<String, Object>) getResponse.getBody().get("metadata");
        assertThat(metadata.get("titleLocked")).isEqualTo(true);
    }

    @Test
    void adminCanConsolidateMetadata() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book1 = createPhantomBook(library, "Consolidate One " + UUID.randomUUID());
        BookEntity book2 = createPhantomBook(library, "Consolidate Two " + UUID.randomUUID());
        String categoryAlpha = "Alpha Category " + UUID.randomUUID();
        String categoryBeta = "Beta Category " + UUID.randomUUID();

        BulkMetadataUpdateRequest bulkAlpha = new BulkMetadataUpdateRequest();
        bulkAlpha.setBookIds(Set.of(book1.getId()));
        bulkAlpha.setGenres(Set.of(categoryAlpha));
        rest.exchange(
                baseUrl() + "/api/v1/books/bulk-edit-metadata",
                HttpMethod.PUT,
                auth.bearerEntity(bulkAlpha, tokens.accessToken()),
                Void.class
        );

        BulkMetadataUpdateRequest bulkBeta = new BulkMetadataUpdateRequest();
        bulkBeta.setBookIds(Set.of(book2.getId()));
        bulkBeta.setGenres(Set.of(categoryBeta));
        rest.exchange(
                baseUrl() + "/api/v1/books/bulk-edit-metadata",
                HttpMethod.PUT,
                auth.bearerEntity(bulkBeta, tokens.accessToken()),
                Void.class
        );

        MergeMetadataRequest request = new MergeMetadataRequest();
        request.setMetadataType(MergeMetadataType.categories);
        request.setTargetValues(List.of(categoryAlpha));
        request.setValuesToMerge(List.of(categoryBeta));

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/books/metadata/manage/consolidate",
                HttpMethod.POST,
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book2.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> metadata = (Map<String, Object>) getResponse.getBody().get("metadata");
        assertThat(metadata.get("categories")).asList().containsExactly(categoryAlpha);
    }

    @Test
    void adminCanDeleteMetadata() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = createPhantomBook(library, "Delete Metadata Book " + UUID.randomUUID());
        String category = "Gamma Category " + UUID.randomUUID();

        BulkMetadataUpdateRequest bulk = new BulkMetadataUpdateRequest();
        bulk.setBookIds(Set.of(book.getId()));
        bulk.setGenres(Set.of(category));
        rest.exchange(
                baseUrl() + "/api/v1/books/bulk-edit-metadata",
                HttpMethod.PUT,
                auth.bearerEntity(bulk, tokens.accessToken()),
                Void.class
        );

        DeleteMetadataRequest request = new DeleteMetadataRequest();
        request.setMetadataType(MergeMetadataType.categories);
        request.setValuesToDelete(List.of(category));

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/books/metadata/manage/delete",
                HttpMethod.POST,
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/books/" + book.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> metadata = (Map<String, Object>) getResponse.getBody().get("metadata");
        assertThat(metadata.get("categories")).asList().isEmpty();
    }

    @Test
    void regularUserDeniedMetadataManagement() throws Exception {
        BookLoreUserEntity user = auth.createUser("metadata-mgmt-denied", "password");
        LibraryEntity library = createLibrary();
        data.assignLibraryToUser(user, library);
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        MergeMetadataRequest consolidate = new MergeMetadataRequest();
        consolidate.setMetadataType(MergeMetadataType.categories);
        consolidate.setTargetValues(List.of("A"));
        consolidate.setValuesToMerge(List.of("B"));

        ResponseEntity<Void> consolidateResponse = rest.exchange(
                baseUrl() + "/api/v1/books/metadata/manage/consolidate",
                HttpMethod.POST,
                auth.bearerEntity(consolidate, tokens.accessToken()),
                Void.class
        );
        assertThat(consolidateResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        DeleteMetadataRequest delete = new DeleteMetadataRequest();
        delete.setMetadataType(MergeMetadataType.categories);
        delete.setValuesToDelete(List.of("A"));

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/books/metadata/manage/delete",
                HttpMethod.POST,
                auth.bearerEntity(delete, tokens.accessToken()),
                Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
