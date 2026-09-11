package org.booklore.it.util;

import org.booklore.model.entity.*;
import org.booklore.model.enums.*;
import org.booklore.model.dto.OpdsUserV2;
import org.booklore.repository.jooq.JooqAuthorRepository;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookMetadataRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.repository.jooq.dto.AuthorRow;
import org.booklore.repository.LibraryPathRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.jooq.JooqOpdsUserV2Repository;
import org.booklore.repository.jooq.JooqShelfRepository;
import org.booklore.repository.jooq.JooqUserLibraryRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class TestDataFactory {

    private final LibraryRepository libraryRepository;
    private final LibraryPathRepository libraryPathRepository;
    private final JooqBookRepository jooqBookRepository;
    private final JooqBookMetadataRepository jooqBookMetadataRepository;
    private final JooqBookEntityRepository jooqBookEntityRepository;
    private final ShelfRepository shelfRepository;
    private final JooqShelfRepository jooqShelfRepository;
    private final JooqUserLibraryRepository jooqUserLibraryRepository;
    private final JooqAuthorRepository jooqAuthorRepository;
    private final JooqOpdsUserV2Repository opdsUserRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataFactory(LibraryRepository libraryRepository,
                           LibraryPathRepository libraryPathRepository,
                           JooqBookRepository jooqBookRepository,
                           JooqBookMetadataRepository jooqBookMetadataRepository,
                           JooqBookEntityRepository jooqBookEntityRepository,
                           ShelfRepository shelfRepository,
                           JooqShelfRepository jooqShelfRepository,
                           JooqUserLibraryRepository jooqUserLibraryRepository,
                           JooqAuthorRepository jooqAuthorRepository,
                           JooqOpdsUserV2Repository opdsUserRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.libraryRepository = libraryRepository;
        this.libraryPathRepository = libraryPathRepository;
        this.jooqBookRepository = jooqBookRepository;
        this.jooqBookMetadataRepository = jooqBookMetadataRepository;
        this.jooqBookEntityRepository = jooqBookEntityRepository;
        this.shelfRepository = shelfRepository;
        this.jooqShelfRepository = jooqShelfRepository;
        this.jooqUserLibraryRepository = jooqUserLibraryRepository;
        this.jooqAuthorRepository = jooqAuthorRepository;
        this.opdsUserRepository = opdsUserRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LibraryEntity createLibrary(String name, Path rootDir) {
        LibraryEntity library = LibraryEntity.builder()
                .name(name)
                .watch(false)
                .organizationMode(LibraryOrganizationMode.AUTO_DETECT)
                .metadataSource(MetadataSource.EMBEDDED)
                .formatPriority(new ArrayList<>(List.of(BookFileType.EPUB, BookFileType.PDF, BookFileType.CBX)))
                .build();

        library = libraryRepository.save(library);

        LibraryPathEntity path = new LibraryPathEntity();
        path.setPath(rootDir.toAbsolutePath().toString());
        path.setLibrary(library);
        libraryPathRepository.save(path);

        return library;
    }

    /** The first (and typically only) configured path of [library], loaded fresh via the JPA repository. */
    public LibraryPathEntity getFirstLibraryPath(LibraryEntity library) {
        List<LibraryPathEntity> paths = libraryPathRepository.findByLibraryId(library.getId());
        if (paths.isEmpty()) {
            throw new IllegalStateException("Library " + library.getId() + " has no configured paths");
        }
        return paths.get(0);
    }

    public LibraryEntity createLibrary(String name, String pathStr) {
        return createLibrary(name, Path.of(pathStr));
    }

    public BookEntity createBook(LibraryEntity library, String title) {
        BookEntity book = BookEntity.builder()
                .library(library)
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

        return insertBook(book);
    }

    /** Persists an in-memory book graph (book + metadata + bookFiles, built via builders the same way
     * the JPA cascade used to accept) via jOOQ inserts, returning the fully-hydrated persisted entity -
     * a stand-in for the old bookRepository.save(book) cascade. */
    public BookEntity insertBook(BookEntity book) {
        Long libraryId = book.getLibrary().getId();
        Long libraryPathId = book.getLibraryPath() != null ? book.getLibraryPath().getId() : null;
        long bookId = jooqBookRepository.insertBook(libraryId, libraryPathId, book.getAddedOn());
        if (book.getScannedOn() != null) {
            jooqBookRepository.updateScannedOn(bookId, book.getScannedOn());
        }

        if (book.getMetadata() != null) {
            jooqBookMetadataRepository.insertRow(bookId, book.getMetadata());
        }

        if (book.getBookFiles() != null) {
            for (BookFileEntity file : book.getBookFiles()) {
                Long fileId = jooqBookRepository.insertBookFile(
                        bookId, file.getFileName(), file.getFileSubPath(), file.isBookFormat(),
                        file.isFolderBased(), file.getBookType(), file.getFileSizeKb(),
                        file.getInitialHash(), file.getCurrentHash(), file.getDescription(), Instant.now());
                file.setId(fileId);
                jooqBookRepository.updateBookFileFullRow(file);
            }
        }

        return jooqBookEntityRepository.findByIdFull(bookId);
    }

    public void assignLibraryToUser(BookLoreUserEntity user, LibraryEntity library) {
        Set<Long> libraryIds = new HashSet<>(jooqUserLibraryRepository.findLibraryIdsByUserId(user.getId()));
        libraryIds.add(library.getId());
        jooqUserLibraryRepository.setLibrariesForUser(user.getId(), libraryIds);
    }

    public BookEntity createBookWithFile(LibraryEntity library, String title, BookFileType bookType, Path fixtureFile) throws IOException {
        String extension = primaryExtension(bookType);
        String subPath = "books";
        LibraryPathEntity libraryPath = getFirstLibraryPath(library);
        Path libraryDir = Path.of(libraryPath.getPath());
        Path targetDir = libraryDir.resolve(subPath);
        Files.createDirectories(targetDir);
        String fileName = title + "." + extension;
        Path target = targetDir.resolve(fileName);
        Files.copy(fixtureFile, target);

        long fileSizeKb = Math.max(1L, Files.size(target) / 1024L);

        BookEntity book = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
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

        BookFileEntity bookFile = BookFileEntity.builder()
                .book(book)
                .fileName(fileName)
                .fileSubPath(subPath)
                .isBookFormat(true)
                .bookType(bookType)
                .fileSizeKb(fileSizeKb)
                .currentHash(UUID.randomUUID().toString())
                .build();
        book.setBookFiles(List.of(bookFile));

        return insertBook(book);
    }

    private String primaryExtension(BookFileType bookType) {
        return switch (bookType) {
            case CBX -> "cbz";
            case AUDIOBOOK -> "mp3";
            default -> bookType.getExtensions().iterator().next();
        };
    }

    public AuthorRow createAuthor(String name) {
        return jooqAuthorRepository.save(new AuthorRow(null, name, null, null, false, false, false, false));
    }

    public ShelfEntity createShelf(BookLoreUserEntity user, String name, boolean isPublic) {
        ShelfEntity shelf = ShelfEntity.builder()
                .user(user)
                .name(name)
                .isPublic(isPublic)
                .build();
        return shelfRepository.save(shelf);
    }

    public void addBookToShelf(BookEntity book, ShelfEntity shelf) {
        jooqShelfRepository.assignShelfToBook(book.getId(), shelf.getId());
    }

    public OpdsUserV2 createOpdsUser(BookLoreUserEntity user, String username, String rawPassword) {
        return opdsUserRepository.insert(
                user.getId(), username, passwordEncoder.encode(rawPassword), OpdsSortOrder.RECENT);
    }
}
