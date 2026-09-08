package org.booklore.service.book;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.repository.*;
import org.booklore.repository.jooq.JooqBookMetadataRelationsRepository;
import org.booklore.repository.jooq.JooqBookMetadataRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.repository.jooq.JooqComicMetadataRepository;
import org.booklore.service.file.FileFingerprint;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BookCreatorService {

    private final JooqBookMetadataRelationsRepository relationsRepository;
    private final BookRepository bookRepository;
    private final JooqBookRepository jooqBookRepository;
    private final JooqBookMetadataRepository jooqBookMetadataRepository;
    private final JooqComicMetadataRepository comicRelationsRepository;

    // Temporary storage for comic metadata DTOs during processing
    private final Map<Long, ComicMetadata> pendingComicMetadata = new ConcurrentHashMap<>();

    public BookCreatorService(
                              JooqBookMetadataRelationsRepository relationsRepository,
                              BookRepository bookRepository,
                              JooqBookRepository jooqBookRepository,
                              JooqBookMetadataRepository jooqBookMetadataRepository,
                              JooqComicMetadataRepository comicRelationsRepository) {
        this.relationsRepository = relationsRepository;
        this.bookRepository = bookRepository;
        this.jooqBookRepository = jooqBookRepository;
        this.jooqBookMetadataRepository = jooqBookMetadataRepository;
        this.comicRelationsRepository = comicRelationsRepository;
    }

    public BookEntity createShellBook(LibraryFile libraryFile, BookFileType bookFileType) {
        Optional<BookEntity> existingBookOpt = bookRepository.findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
                libraryFile.getLibraryEntity().getId(),
                libraryFile.getLibraryPathEntity().getId(),
                libraryFile.getFileSubPath(),
                libraryFile.getFileName());

        if (existingBookOpt.isPresent()) {
            log.warn("Book already exists for file: {}", libraryFile.getFileName());
            long fileSizeKb = calculateFileSize(libraryFile);
            String newHash = libraryFile.isFolderBased()
                    ? FileFingerprint.generateFolderHash(libraryFile.getFullPath())
                    : FileFingerprint.generateHash(libraryFile.getFullPath());
            BookEntity existingBook = existingBookOpt.get();
            BookFileEntity primaryFile = existingBook.getPrimaryBookFile();
            primaryFile.setCurrentHash(newHash);
            primaryFile.setInitialHash(newHash);
            primaryFile.setFileSizeKb(fileSizeKb);
            primaryFile.setFolderBased(libraryFile.isFolderBased());
            existingBook.setDeleted(false);
            jooqBookRepository.markUndeleted(List.of(existingBook.getId()));
            return existingBook;
        }

        long fileSizeKb = calculateFileSize(libraryFile);
        String hash = libraryFile.isFolderBased()
                ? FileFingerprint.generateFolderHash(libraryFile.getFullPath())
                : FileFingerprint.generateHash(libraryFile.getFullPath());
        Instant now = Instant.now();

        long bookId = jooqBookRepository.insertBook(
                libraryFile.getLibraryEntity().getId(),
                libraryFile.getLibraryPathEntity().getId(),
                now);

        BookEntity bookEntity = BookEntity.builder()
                .library(libraryFile.getLibraryEntity())
                .libraryPath(libraryFile.getLibraryPathEntity())
                .addedOn(now)
                .bookFiles(new ArrayList<>())
                .build();
        bookEntity.setId(bookId);

        BookFileEntity bookFileEntity = BookFileEntity.builder()
                .book(bookEntity)
                .fileName(libraryFile.getFileName())
                .fileSubPath(libraryFile.getFileSubPath())
                .isBookFormat(true)
                .folderBased(libraryFile.isFolderBased())
                .bookType(bookFileType)
                .fileSizeKb(fileSizeKb)
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(now)
                .build();
        long fileId = jooqBookRepository.insertBookFile(
                bookId,
                bookFileEntity.getFileName(),
                bookFileEntity.getFileSubPath(),
                bookFileEntity.isBookFormat(),
                bookFileEntity.isFolderBased(),
                bookFileEntity.getBookType(),
                bookFileEntity.getFileSizeKb(),
                bookFileEntity.getInitialHash(),
                bookFileEntity.getCurrentHash(),
                bookFileEntity.getDescription(),
                bookFileEntity.getAddedOn());
        bookFileEntity.setId(fileId);
        bookEntity.getBookFiles().add(bookFileEntity);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(bookEntity)
                .bookId(bookId)
                .build();
        bookEntity.setMetadata(metadata);
        jooqBookMetadataRepository.insertRow(bookId, metadata);

        return bookEntity;
    }

    private long calculateFileSize(LibraryFile libraryFile) {
        if (libraryFile.isFolderBased()) {
            Long size = FileUtils.getFolderSizeInKb(libraryFile.getFullPath());
            return size != null ? size : 0L;
        } else {
            Long size = FileUtils.getFileSizeInKb(libraryFile.getFullPath());
            return size != null ? size : 0L;
        }
    }

    public void addCategoriesToBook(Set<String> categories, BookEntity bookEntity) {
        if (categories == null || categories.isEmpty()) return;
        Set<String> truncated = categories.stream().map(cat -> truncate(cat, 255)).collect(Collectors.toSet());
        relationsRepository.setCategoriesForBook(bookEntity.getId(), truncated);
    }

    public void addAuthorsToBook(Collection<String> authors, BookEntity bookEntity) {
        if (authors == null || authors.isEmpty()) return;
        List<String> truncated = authors.stream().map(name -> truncate(name, 255)).toList();
        relationsRepository.setAuthorsForBook(bookEntity.getId(), truncated);
        bookEntity.getMetadata().setAuthorNames(truncated);
        bookEntity.getMetadata().updateSearchText(); // Manually trigger search text update since a jOOQ write doesn't dirty-check the entity
    }

    public void addMoodsToBook(Set<String> moods, BookEntity bookEntity) {
        if (moods == null || moods.isEmpty()) return;
        Set<String> truncated = moods.stream().map(mood -> truncate(mood, 255)).collect(Collectors.toSet());
        relationsRepository.setMoodsForBook(bookEntity.getId(), truncated);
    }

    public void addTagsToBook(Set<String> tags, BookEntity bookEntity) {
        if (tags == null || tags.isEmpty()) return;
        Set<String> truncated = tags.stream().map(tag -> truncate(tag, 255)).collect(Collectors.toSet());
        relationsRepository.setTagsForBook(bookEntity.getId(), truncated);
    }

    private String truncate(String input, int maxLength) {
        if (input == null)
            return null;
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }

    @Transactional
    public void saveConnections(BookEntity bookEntity) {
        Long bookId = bookEntity.getId();

        BookMetadataEntity metadata = bookEntity.getMetadata();
        metadata.updateSearchText(); // trims fields + recomputes search_text - previously ran via @PrePersist/@PreUpdate
        jooqBookMetadataRepository.updateFullRow(metadata);

        jooqBookRepository.updateMetadataMatchScore(bookId, bookEntity.getMetadataMatchScore());
        jooqBookRepository.updateBookFileFullRow(bookEntity.getPrimaryBookFile());

        if (bookEntity.getBookCoverHash() != null) {
            jooqBookRepository.updateBookCoverHash(bookId, bookEntity.getBookCoverHash());
        }
        if (bookEntity.getAudiobookCoverHash() != null) {
            jooqBookRepository.updateAudiobookCoverHash(bookId, bookEntity.getAudiobookCoverHash());
        }

        // Save comic metadata if present
        ComicMetadataEntity comicMetadata = metadata.getComicMetadata();
        if (comicMetadata != null && comicMetadata.getBookId() != null) {
            // Mirrors JPA save()'s merge-or-insert semantics for comic_metadata's manually-assigned
            // id: CbxProcessor always builds a fresh ComicMetadataEntity, even on a rescan of a book
            // that already has a comic_metadata row, so this can't assume "always new".
            comicRelationsRepository.ensureRowExists(comicMetadata.getBookId());
            comicRelationsRepository.updateFullRow(comicMetadata);

            // Populate relationships from pending DTO
            ComicMetadata comicDto = pendingComicMetadata.remove(bookId);
            if (comicDto != null) {
                populateComicMetadataRelationships(comicMetadata, comicDto);
            }
        }
    }

    public void setComicMetadataDto(BookEntity bookEntity, ComicMetadata comicDto) {
        if (bookEntity.getId() != null && comicDto != null) {
            pendingComicMetadata.put(bookEntity.getId(), comicDto);
        }
    }

    private void populateComicMetadataRelationships(ComicMetadataEntity comic, ComicMetadata dto) {
        Long bookId = comic.getBookId();

        if (dto.getCharacters() != null && !dto.getCharacters().isEmpty()) {
            Set<String> names = dto.getCharacters().stream().map(name -> truncate(name, 255)).collect(Collectors.toSet());
            comicRelationsRepository.setCharactersForBook(bookId, names);
            comic.setCharacterNames(names);
        }

        if (dto.getTeams() != null && !dto.getTeams().isEmpty()) {
            Set<String> names = dto.getTeams().stream().map(name -> truncate(name, 255)).collect(Collectors.toSet());
            comicRelationsRepository.setTeamsForBook(bookId, names);
            comic.setTeamNames(names);
        }

        if (dto.getLocations() != null && !dto.getLocations().isEmpty()) {
            Set<String> names = dto.getLocations().stream().map(name -> truncate(name, 255)).collect(Collectors.toSet());
            comicRelationsRepository.setLocationsForBook(bookId, names);
            comic.setLocationNames(names);
        }

        addCreatorsWithRole(comic, dto.getPencillers(), ComicCreatorRole.PENCILLER);
        addCreatorsWithRole(comic, dto.getInkers(), ComicCreatorRole.INKER);
        addCreatorsWithRole(comic, dto.getColorists(), ComicCreatorRole.COLORIST);
        addCreatorsWithRole(comic, dto.getLetterers(), ComicCreatorRole.LETTERER);
        addCreatorsWithRole(comic, dto.getCoverArtists(), ComicCreatorRole.COVER_ARTIST);
        addCreatorsWithRole(comic, dto.getEditors(), ComicCreatorRole.EDITOR);
    }

    private void addCreatorsWithRole(ComicMetadataEntity comic, Set<String> names, ComicCreatorRole role) {
        if (names == null || names.isEmpty()) {
            return;
        }
        Set<String> truncated = names.stream().map(name -> truncate(name, 255)).collect(Collectors.toSet());
        comicRelationsRepository.setCreatorsForBookRole(comic.getBookId(), role, truncated);
        switch (role) {
            case PENCILLER -> comic.setPencillerNames(truncated);
            case INKER -> comic.setInkerNames(truncated);
            case COLORIST -> comic.setColoristNames(truncated);
            case LETTERER -> comic.setLettererNames(truncated);
            case COVER_ARTIST -> comic.setCoverArtistNames(truncated);
            case EDITOR -> comic.setEditorNames(truncated);
        }
    }
}
