package org.booklore.it.util;

import org.booklore.model.entity.*;
import org.booklore.model.enums.*;
import org.booklore.model.dto.OpdsUserV2;
import org.booklore.repository.AuthorRepository;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.CategoryRepository;
import org.booklore.repository.LibraryPathRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.jooq.JooqOpdsUserV2Repository;
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
    private final BookRepository bookRepository;
    private final BookMetadataRepository bookMetadataRepository;
    private final ShelfRepository shelfRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final JooqOpdsUserV2Repository opdsUserRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataFactory(LibraryRepository libraryRepository,
                           LibraryPathRepository libraryPathRepository,
                           BookRepository bookRepository,
                           BookMetadataRepository bookMetadataRepository,
                           ShelfRepository shelfRepository,
                           AuthorRepository authorRepository,
                           CategoryRepository categoryRepository,
                           JooqOpdsUserV2Repository opdsUserRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.libraryRepository = libraryRepository;
        this.libraryPathRepository = libraryPathRepository;
        this.bookRepository = bookRepository;
        this.bookMetadataRepository = bookMetadataRepository;
        this.shelfRepository = shelfRepository;
        this.authorRepository = authorRepository;
        this.categoryRepository = categoryRepository;
        this.opdsUserRepository = opdsUserRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LibraryEntity createLibrary(String name, Path rootDir) {
        LibraryPathEntity path = new LibraryPathEntity();
        path.setPath(rootDir.toAbsolutePath().toString());

        LibraryEntity library = LibraryEntity.builder()
                .name(name)
                .watch(false)
                .organizationMode(LibraryOrganizationMode.AUTO_DETECT)
                .metadataSource(MetadataSource.EMBEDDED)
                .formatPriority(new ArrayList<>(List.of(BookFileType.EPUB, BookFileType.PDF, BookFileType.CBX)))
                .build();

        path.setLibrary(library);
        library.setLibraryPaths(new ArrayList<>(List.of(path)));

        return libraryRepository.save(library);
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

        return bookRepository.save(book);
    }

    public void assignLibraryToUser(BookLoreUserEntity user, LibraryEntity library) {
        Set<LibraryEntity> libraries = new HashSet<>(user.getLibraries());
        libraries.add(library);
        user.setLibraries(libraries);
        userRepository.save(user);
    }

    public BookEntity createBookWithFile(LibraryEntity library, String title, BookFileType bookType, Path fixtureFile) throws IOException {
        String extension = primaryExtension(bookType);
        String subPath = "books";
        Path libraryDir = Path.of(library.getLibraryPaths().get(0).getPath());
        Path targetDir = libraryDir.resolve(subPath);
        Files.createDirectories(targetDir);
        String fileName = title + "." + extension;
        Path target = targetDir.resolve(fileName);
        Files.copy(fixtureFile, target);

        long fileSizeKb = Math.max(1L, Files.size(target) / 1024L);

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

        return bookRepository.save(book);
    }

    private String primaryExtension(BookFileType bookType) {
        return switch (bookType) {
            case CBX -> "cbz";
            case AUDIOBOOK -> "mp3";
            default -> bookType.getExtensions().iterator().next();
        };
    }

    public AuthorEntity createAuthor(String name) {
        AuthorEntity author = AuthorEntity.builder().name(name).build();
        return authorRepository.save(author);
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
        // BookShelfMapping was dropped; book_shelf_mapping is now written through the
        // ShelfEntity <-> BookEntity @ManyToMany join table.
        shelf.getBookEntities().add(book);
        shelfRepository.save(shelf);
    }

    public OpdsUserV2 createOpdsUser(BookLoreUserEntity user, String username, String rawPassword) {
        return opdsUserRepository.insert(
                user.getId(), username, passwordEncoder.encode(rawPassword), OpdsSortOrder.RECENT);
    }
}
