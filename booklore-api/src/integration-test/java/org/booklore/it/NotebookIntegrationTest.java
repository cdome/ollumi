package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMarkEntity;
import org.booklore.model.entity.BookNoteV2Entity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.AnnotationRepository;
import org.booklore.repository.BookMarkRepository;
import org.booklore.repository.BookNoteV2Repository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class NotebookIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private AnnotationRepository annotationRepository;

    @Autowired
    private BookNoteV2Repository bookNoteV2Repository;

    @Autowired
    private BookMarkRepository bookMarkRepository;

    @Test
    void getNotebookEntriesReturnsSeededAnnotations() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Notebook Test Book");
        seedNotebookEntries(book);

        String url = UriComponentsBuilder.fromUriString(baseUrl() + "/api/v1/notebook")
                .queryParam("page", 0)
                .queryParam("size", 50)
                .queryParam("types", "HIGHLIGHT", "NOTE", "BOOKMARK")
                .queryParam("bookId", book.getId())
                .queryParam("sort", "desc")
                .toUriString();

        ResponseEntity<Map> response = rest.exchange(
                url,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(3);
        assertThat(content.stream().map(e -> e.get("type"))).containsExactlyInAnyOrder("HIGHLIGHT", "NOTE", "BOOKMARK");
    }

    @Test
    void exportNotebookEntriesReturnsAllEntries() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Notebook Export Book");
        seedNotebookEntries(book);

        String url = UriComponentsBuilder.fromUriString(baseUrl() + "/api/v1/notebook/export")
                .queryParam("types", "HIGHLIGHT", "NOTE", "BOOKMARK")
                .queryParam("bookId", book.getId())
                .queryParam("sort", "desc")
                .toUriString();

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                url,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(3);
    }

    @Test
    void getBooksWithAnnotationsReturnsBook() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Notebook Books Book");
        seedNotebookEntries(book);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/notebook/books",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .anyMatch(b -> book.getId().equals(((Number) b.get("bookId")).longValue()));
    }

    @Test
    void notebookEntriesCanBeFilteredByBookId() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        LibraryEntity library = createLibrary();
        BookEntity book = data.createBook(library, "Filtered Book");
        BookEntity otherBook = data.createBook(library, "Other Book");
        seedNotebookEntries(book);

        String url = UriComponentsBuilder.fromUriString(baseUrl() + "/api/v1/notebook")
                .queryParam("page", 0)
                .queryParam("size", 50)
                .queryParam("types", "HIGHLIGHT", "NOTE", "BOOKMARK")
                .queryParam("bookId", book.getId())
                .queryParam("sort", "desc")
                .toUriString();

        ResponseEntity<Map> response = rest.exchange(
                url,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(3);
        assertThat(content).allMatch(e -> book.getId().equals(((Number) e.get("bookId")).longValue()));
    }

    private LibraryEntity createLibrary() throws Exception {
        Path tempDir = Files.createTempDirectory("notebook-it-");
        return data.createLibrary("NotebookLib " + UUID.randomUUID(), tempDir);
    }

    private void seedNotebookEntries(BookEntity book) {
        BookLoreUserEntity user = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();

        annotationRepository.save(AnnotationEntity.builder()
                .user(user)
                .book(book)
                .cfi("epubcfi(/6/2[id001]!/4/1:0)")
                .text("Highlighted text")
                .note("annotation note")
                .color("yellow")
                .style("solid")
                .chapterTitle("Chapter One")
                .build());

        bookNoteV2Repository.save(BookNoteV2Entity.builder()
                .user(user)
                .book(book)
                .cfi("epubcfi(/6/2[id002]!/4/1:0)")
                .selectedText("Selected text")
                .noteContent("This is a note")
                .color("blue")
                .chapterTitle("Chapter Two")
                .build());

        bookMarkRepository.save(BookMarkEntity.builder()
                .user(user)
                .book(book)
                .title("Important bookmark")
                .notes("bookmark notes")
                .color("red")
                .priority(1)
                .build());
    }
}
