package org.booklore.service.book;

import org.booklore.exception.ApiError;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.request.CreatePhysicalBookRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqLibraryRepository;
import org.booklore.repository.jooq.JooqBookMetadataRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class PhysicalBookService {

    private final JooqBookEntityRepository jooqBookEntityRepository;
    private final JooqLibraryRepository libraryRepository;
    private final org.booklore.repository.jooq.JooqBookMetadataRelationsRepository relationsRepository;
    private final JooqBookRepository jooqBookRepository;
    private final JooqBookMetadataRepository jooqBookMetadataRepository;
    private final BookMapper bookMapper;
    private final FileService fileService;

    @Transactional
    public Book createPhysicalBook(CreatePhysicalBookRequest request) {
        LibraryEntity library = libraryRepository.findById(request.getLibraryId());
        if (library == null) {
            throw new APIException("Library not found with id: " + request.getLibraryId(), HttpStatus.NOT_FOUND);
        }

        Instant now = Instant.now();
        long bookId = jooqBookRepository.insertBook(library.getId(), null, now);

        BookEntity bookEntity = BookEntity.builder()
                .library(library)
                .libraryPath(null)
                .isPhysical(true)
                .addedOn(now)
                .scannedOn(now)
                .bookFiles(new ArrayList<>())
                .build();
        bookEntity.setId(bookId);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(bookEntity)
                .bookId(bookId)
                .title(request.getTitle())
                .description(request.getDescription())
                .publisher(request.getPublisher())
                .publishedDate(parsePublishedDate(request.getPublishedDate()))
                .language(request.getLanguage())
                .pageCount(request.getPageCount())
                .isbn13(extractIsbn13(request.getIsbn()))
                .isbn10(extractIsbn10(request.getIsbn()))
                .build();
        bookEntity.setMetadata(metadata);
        jooqBookMetadataRepository.insertRow(bookId, metadata);

        log.info("Created physical book '{}' in library {} with id {}", request.getTitle(), library.getName(), bookId);

        if (request.getAuthors() != null && !request.getAuthors().isEmpty()) {
            addAuthorsToBook(new ArrayList<>(request.getAuthors()), bookEntity);
        }

        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            addCategoriesToBook(new HashSet<>(request.getCategories()), bookEntity);
        }

        if (request.getThumbnailUrl() != null && !request.getThumbnailUrl().isBlank()) {
            try {
                fileService.createThumbnailFromUrl(bookId, request.getThumbnailUrl());
                metadata.setCoverUpdatedOn(Instant.now());
                bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
            } catch (Exception ex) {
                log.warn("Failed to download cover for physical book {}: {}", bookId, ex.getMessage());
            }
        }

        // Comprehensive flush point (mirrors BookCreatorService.saveConnections): writes the final
        // in-memory state - including whatever addAuthorsToBook/cover download mutated above - since
        // this entity is never Hibernate-managed and so never auto-flushes on its own.
        metadata.updateSearchText();
        jooqBookMetadataRepository.updateFullRow(metadata);
        if (bookEntity.getBookCoverHash() != null) {
            jooqBookRepository.updateBookCoverHash(bookId, bookEntity.getBookCoverHash());
        }

        return bookMapper.toBook(bookEntity);
    }

    private LocalDate parsePublishedDate(String publishedDate) {
        if (publishedDate == null || publishedDate.isBlank()) {
            return null;
        }
        String trimmed = publishedDate.trim();
        // Try full date format first (YYYY-MM-DD)
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {}
        // Try year only format (YYYY)
        try {
            int year = Integer.parseInt(trimmed);
            return LocalDate.of(year, 1, 1);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private String extractIsbn13(String isbn) {
        if (isbn == null) return null;
        String cleaned = isbn.replaceAll("[^0-9X]", "");
        return cleaned.length() == 13 ? cleaned : null;
    }

    private String extractIsbn10(String isbn) {
        if (isbn == null) return null;
        String cleaned = isbn.replaceAll("[^0-9X]", "");
        return cleaned.length() == 10 ? cleaned : null;
    }

    private void addAuthorsToBook(List<String> authors, BookEntity bookEntity) {
        List<String> truncated = authors.stream().map(name -> truncate(name, 255)).toList();
        relationsRepository.setAuthorsForBook(bookEntity.getId(), truncated);
        bookEntity.getMetadata().setAuthorNames(truncated);
        bookEntity.getMetadata().updateSearchText();
    }

    private void addCategoriesToBook(Set<String> categories, BookEntity bookEntity) {
        Set<String> truncated = categories.stream().map(cat -> truncate(cat, 255)).collect(Collectors.toSet());
        relationsRepository.setCategoriesForBook(bookEntity.getId(), truncated);
    }

    @Transactional
    public Book togglePhysicalFlag(long bookId, boolean physical) {
        BookEntity book = jooqBookEntityRepository.findByIdFull(bookId);
        if (book == null) {
            throw ApiError.BOOK_NOT_FOUND.createException(bookId);
        }
        book.setIsPhysical(physical);
        jooqBookRepository.updateIsPhysical(bookId, physical);
        log.info("Book {} physical flag set to {}", bookId, physical);
        return bookMapper.toBook(book);
    }

    private String truncate(String input, int maxLength) {
        if (input == null) return null;
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }
}
