package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.dto.request.FileMoveRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.jooq.JooqLibraryRepository;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqLibraryPathRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class FileMoveIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private JooqLibraryRepository libraryRepository;

    @Autowired
    private JooqLibraryPathRepository libraryPathRepository;

    @Autowired
    private JooqBookEntityRepository jooqBookEntityRepository;

    @Test
    void adminCanMoveBookFileBetweenLibraryPaths() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Path path1Dir = Files.createTempDirectory("file-move-it-1-");
        Path path2Dir = Files.createTempDirectory("file-move-it-2-");

        LibraryEntity library = data.createLibrary("MoveLib " + UUID.randomUUID(), path1Dir);

        LibraryPathEntity path2 = new LibraryPathEntity();
        path2.setPath(path2Dir.toAbsolutePath().toString());
        path2.setLibrary(library);
        path2.setId(libraryPathRepository.insertLibraryPath(library.getId(), path2.getPath()));

        Path pdf = Files.createTempFile("move-source-", ".pdf");
        FixtureFactory.writePdf(pdf);

        BookEntity book = data.createBookWithFile(
                library, "Move Book " + UUID.randomUUID(), BookFileType.PDF, pdf);

        Long sourcePathId = book.getLibraryPath().getId();
        Long targetPathId = path2.getId();

        FileMoveRequest.Move move = new FileMoveRequest.Move(book.getId(), library.getId(), targetPathId);
        FileMoveRequest request = new FileMoveRequest(Set.of(book.getId()), List.of(move));

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/files/move",
                HttpMethod.POST,
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        BookEntity moved = jooqBookEntityRepository.findByIdFull(book.getId());
        assertThat(moved).isNotNull();
        assertThat(moved.getLibraryPath().getId()).isEqualTo(targetPathId);
        assertThat(moved.getLibraryPath().getId()).isNotEqualTo(sourcePathId);
        assertThat(Files.exists(moved.getBookFiles().get(0).getFullFilePath())).isTrue();
    }
}
