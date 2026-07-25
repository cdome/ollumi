package org.booklore.app.service;

import lombok.AllArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.app.dto.AppBookDetail;
import org.booklore.app.dto.AppBookSummary;
import org.booklore.app.dto.AppFilterOptions;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.jooq.AppBookConditions;
import org.booklore.repository.jooq.JooqAppBookRepository;
import org.booklore.service.opds.MagicShelfBookService;
import org.jooq.Condition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AppBookService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final BookRepository bookRepository;
    private final JooqAppBookRepository jooqAppBookRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;
    private final ShelfRepository shelfRepository;
    private final AuthenticationService authenticationService;
    private final AppBookMapper mobileBookMapper;
    private final MagicShelfBookService magicShelfBookService;

    @Transactional(readOnly = true)
    public AppPageResponse<AppBookSummary> getBooks(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Long libraryId,
            Long shelfId,
            ReadStatus status,
            String search,
            BookFileType fileType,
            Integer minRating,
            Integer maxRating,
            String authors,
            String language) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Sort sort = buildSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        Condition condition = buildCondition(
                accessibleLibraryIds, libraryId, shelfId, status, search, userId,
                fileType, minRating, maxRating, authors, language);

        Page<Long> idPage = jooqAppBookRepository.findBookIds(condition, pageable);
        return buildPageResponse(idPage, userId, pageNum, pageSize);
    }

    @Transactional(readOnly = true)
    public AppBookDetail getBookDetail(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (accessibleLibraryIds != null && !accessibleLibraryIds.contains(book.getLibrary().getId())) {
            throw ApiError.FORBIDDEN.createException("Access denied to this book");
        }

        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(userId, bookId)
                .orElse(null);

        UserBookFileProgressEntity fileProgress = userBookFileProgressRepository
                .findMostRecentAudiobookProgressByUserIdAndBookId(userId, bookId)
                .orElse(null);

        return mobileBookMapper.toDetail(book, progress, fileProgress);
    }

    @Transactional(readOnly = true)
    public AppPageResponse<AppBookSummary> searchBooks(
            String query,
            Integer page,
            Integer size) {

        if (query == null || query.trim().isEmpty()) {
            throw ApiError.INVALID_QUERY_PARAMETERS.createException();
        }

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = validatePageNumber(page);
        int pageSize = validatePageSize(size);

        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "addedOn"));

        Condition condition = AppBookConditions.notDeleted()
                .and(AppBookConditions.hasDigitalFile())
                .and(AppBookConditions.inLibraries(accessibleLibraryIds))
                .and(AppBookConditions.searchText(query));

        Page<Long> idPage = jooqAppBookRepository.findBookIds(condition, pageable);
        return buildPageResponse(idPage, userId, pageNum, pageSize);
    }

    @Transactional(readOnly = true)
    public List<AppBookSummary> getContinueReading(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        Condition condition = AppBookConditions.notDeleted()
                .and(AppBookConditions.hasDigitalFile())
                .and(AppBookConditions.inLibraries(accessibleLibraryIds))
                .and(AppBookConditions.inProgress(userId))
                .and(AppBookConditions.hasNonAudiobookFile());

        List<Long> allIds = jooqAppBookRepository.findAllBookIds(condition);
        if (allIds.isEmpty()) return Collections.emptyList();

        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, new LinkedHashSet<>(allIds));

        List<Long> topIds = allIds.stream()
                .filter(progressMap::containsKey)
                .sorted((id1, id2) -> {
                    Instant t1 = progressMap.get(id1).getLastReadTime();
                    Instant t2 = progressMap.get(id2).getLastReadTime();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .limit(maxItems)
                .collect(Collectors.toList());

        if (topIds.isEmpty()) return Collections.emptyList();

        Map<Long, BookEntity> enrichedMap = bookRepository.findAllForSummaryByIds(topIds)
                .stream().collect(Collectors.toMap(BookEntity::getId, b -> b));

        return topIds.stream()
                .filter(enrichedMap::containsKey)
                .map(id -> mobileBookMapper.toSummary(enrichedMap.get(id), progressMap.get(id)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppBookSummary> getContinueListening(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        Condition condition = AppBookConditions.notDeleted()
                .and(AppBookConditions.hasDigitalFile())
                .and(AppBookConditions.inLibraries(accessibleLibraryIds))
                .and(AppBookConditions.inProgress(userId))
                .and(AppBookConditions.hasAudiobookFile());

        List<Long> allIds = jooqAppBookRepository.findAllBookIds(condition);
        if (allIds.isEmpty()) return Collections.emptyList();

        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, new LinkedHashSet<>(allIds));

        List<Long> topIds = allIds.stream()
                .filter(progressMap::containsKey)
                .sorted((id1, id2) -> {
                    Instant t1 = progressMap.get(id1).getLastReadTime();
                    Instant t2 = progressMap.get(id2).getLastReadTime();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .limit(maxItems)
                .collect(Collectors.toList());

        if (topIds.isEmpty()) return Collections.emptyList();

        Map<Long, BookEntity> enrichedMap = bookRepository.findAllForSummaryByIds(topIds)
                .stream().collect(Collectors.toMap(BookEntity::getId, b -> b));

        return topIds.stream()
                .filter(enrichedMap::containsKey)
                .map(id -> mobileBookMapper.toSummary(enrichedMap.get(id), progressMap.get(id)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppBookSummary> getRecentlyAdded(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        Condition condition = AppBookConditions.notDeleted()
                .and(AppBookConditions.hasDigitalFile())
                .and(AppBookConditions.inLibraries(accessibleLibraryIds))
                .and(AppBookConditions.addedWithinDays(30));

        Pageable pageable = PageRequest.of(0, maxItems, Sort.by(Sort.Direction.DESC, "addedOn"));
        Page<Long> idPage = jooqAppBookRepository.findBookIds(condition, pageable);
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) return Collections.emptyList();

        List<BookEntity> enriched = bookRepository.findAllForSummaryByIds(ids);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, enriched);
        Map<Long, BookEntity> enrichedMap = enriched.stream()
                .collect(Collectors.toMap(BookEntity::getId, b -> b));

        return ids.stream()
                .filter(enrichedMap::containsKey)
                .map(id -> mobileBookMapper.toSummary(enrichedMap.get(id), progressMap.get(id)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppBookSummary> getRecentlyScanned(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        Condition condition = AppBookConditions.notDeleted()
                .and(AppBookConditions.hasScannedOn())
                .and(AppBookConditions.inLibraries(accessibleLibraryIds));

        Pageable pageable = PageRequest.of(0, maxItems, Sort.by(Sort.Direction.DESC, "scannedOn"));
        Page<Long> idPage = jooqAppBookRepository.findBookIds(condition, pageable);
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) return Collections.emptyList();

        List<BookEntity> enriched = bookRepository.findAllForSummaryByIds(ids);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, enriched);
        Map<Long, BookEntity> enrichedMap = enriched.stream()
                .collect(Collectors.toMap(BookEntity::getId, b -> b));

        return ids.stream()
                .filter(enrichedMap::containsKey)
                .map(id -> mobileBookMapper.toSummary(enrichedMap.get(id), progressMap.get(id)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppPageResponse<AppBookSummary> getRandomBooks(
            Integer page,
            Integer size,
            Long libraryId) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = validatePageNumber(page);
        int pageSize = validatePageSize(size);

        Condition condition = buildBaseCondition(accessibleLibraryIds, libraryId);

        long totalElements = jooqAppBookRepository.countBooks(condition);

        if (totalElements == 0) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        long maxOffset = Math.max(0, totalElements - pageSize);
        int randomOffset = ThreadLocalRandom.current().nextInt((int) maxOffset + 1);

        Pageable pageable = PageRequest.of(randomOffset / pageSize, pageSize);
        Page<Long> idPage = jooqAppBookRepository.findBookIds(condition, pageable);

        return buildPageResponse(idPage, userId, pageNum, pageSize);
    }

    @Transactional(readOnly = true)
    public AppPageResponse<AppBookSummary> getBooksByMagicShelf(
            Long magicShelfId,
            Integer page,
            Integer size) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        int pageNum = validatePageNumber(page);
        int pageSize = validatePageSize(size);

        var booksPage = magicShelfBookService.getBooksByMagicShelfId(userId, magicShelfId, pageNum, pageSize);

        Set<Long> bookIds = booksPage.getContent().stream()
                .map(Book::getId)
                .collect(Collectors.toSet());

        if (bookIds.isEmpty()) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        List<BookEntity> bookEntities = bookRepository.findAllForSummaryByIds(bookIds);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, bookEntities);

        List<AppBookSummary> summaries = bookEntities.stream()
                .filter(BookEntity::hasFiles)
                .map(bookEntity -> mobileBookMapper.toSummary(bookEntity, progressMap.get(bookEntity.getId())))
                .collect(Collectors.toList());

        return AppPageResponse.of(summaries, pageNum, pageSize, booksPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AppFilterOptions getFilterOptions(Long libraryId, Long shelfId, Long magicShelfId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        // Resolve magic shelf to a set of book IDs if requested
        Set<Long> magicBookIds = null;
        if (magicShelfId != null) {
            magicBookIds = resolveMagicShelfBookIds(magicShelfId, userId);
            if (magicBookIds.isEmpty()) {
                return AppFilterOptions.builder()
                        .authors(Collections.emptyList())
                        .languages(Collections.emptyList())
                        .fileTypes(Collections.emptyList())
                        .readStatuses(getReadStatusOptions())
                        .build();
            }
        }

        // Validate library access
        if (libraryId != null && accessibleLibraryIds != null && !accessibleLibraryIds.contains(libraryId)) {
            throw ApiError.FORBIDDEN.createException("Access denied to library " + libraryId);
        }

        // Validate shelf access
        if (shelfId != null) {
            ShelfEntity shelf = shelfRepository.findById(shelfId)
                    .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
            if (!shelf.isPublic() && !shelf.getUser().getId().equals(userId)) {
                throw ApiError.FORBIDDEN.createException("Access denied to shelf " + shelfId);
            }
        }

        // Build the scope shared by all facet queries
        Condition scope = buildFilterScope(accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        // Authors with book count (top 200 by count)
        List<AppFilterOptions.AuthorOption> authors = jooqAppBookRepository.findAuthorFacets(scope, 200).stream()
                .map(f -> AppFilterOptions.AuthorOption.builder()
                        .name(f.getName())
                        .count(f.getCount())
                        .build())
                .toList();

        // Languages with book count
        List<AppFilterOptions.LanguageOption> languages = jooqAppBookRepository.findLanguageFacets(scope).stream()
                .map(f -> AppFilterOptions.LanguageOption.builder()
                        .code(f.getCode())
                        .label(Locale.forLanguageTag(f.getCode()).getDisplayLanguage(Locale.ENGLISH))
                        .count(f.getCount())
                        .build())
                .toList();

        // Distinct file types present in scoped books
        List<String> fileTypes = jooqAppBookRepository.findFileTypes(scope).stream()
                .sorted()
                .toList();

        return AppFilterOptions.builder()
                .authors(authors)
                .languages(languages)
                .fileTypes(fileTypes)
                .readStatuses(getReadStatusOptions())
                .build();
    }

    private Condition buildFilterScope(Set<Long> accessibleLibraryIds, Long libraryId, Long shelfId, Set<Long> magicBookIds) {
        Condition scope = AppBookConditions.notDeleted()
                .and(AppBookConditions.hasDigitalFile());

        if (magicBookIds != null) {
            scope = scope.and(AppBookConditions.withBookIds(magicBookIds));
        } else if (shelfId != null) {
            scope = scope.and(AppBookConditions.inShelf(shelfId));
        }

        if (libraryId != null) {
            scope = scope.and(AppBookConditions.inLibrary(libraryId));
        } else if (accessibleLibraryIds != null) {
            scope = scope.and(AppBookConditions.inLibraries(accessibleLibraryIds));
        }

        return scope;
    }

    private Set<Long> resolveMagicShelfBookIds(Long magicShelfId, Long userId) {
        // Reuse MagicShelfBookService which already handles access validation,
        // rule evaluation, and library filtering.
        var booksPage = magicShelfBookService.getBooksByMagicShelfId(userId, magicShelfId, 0, 10000);
        return booksPage.getContent().stream()
                .map(Book::getId)
                .collect(Collectors.toSet());
    }

    private List<String> getReadStatusOptions() {
        return Arrays.stream(ReadStatus.values())
                .filter(s -> s != ReadStatus.UNSET)
                .map(Enum::name)
                .toList();
    }

    @Transactional
    public void updateReadStatus(Long bookId, ReadStatus status) {
        UserBookProgressEntity progress = validateAccessAndGetProgress(bookId);

        progress.setReadStatus(status);
        progress.setReadStatusModifiedTime(Instant.now());

        if (status == ReadStatus.READ && progress.getDateFinished() == null) {
            progress.setDateFinished(Instant.now());
        }

        userBookProgressRepository.save(progress);
    }

    @Transactional
    public void updatePersonalRating(Long bookId, Integer rating) {
        UserBookProgressEntity progress = validateAccessAndGetProgress(bookId);

        progress.setPersonalRating(rating);
        userBookProgressRepository.save(progress);
    }

    private UserBookProgressEntity validateAccessAndGetProgress(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        validateLibraryAccess(accessibleLibraryIds, book.getLibrary().getId());

        return userBookProgressRepository
                .findByUserIdAndBookId(userId, bookId)
                .orElseGet(() -> createNewProgress(userId, book));
    }

    private void validateLibraryAccess(Set<Long> accessibleLibraryIds, Long libraryId) {
        if (accessibleLibraryIds != null && !accessibleLibraryIds.contains(libraryId)) {
            throw ApiError.FORBIDDEN.createException("Access denied to this book");
        }
    }

    private UserBookProgressEntity createNewProgress(Long userId, BookEntity book) {
        return UserBookProgressEntity.builder()
                .user(BookLoreUserEntity.builder().id(userId).build())
                .book(book)
                .build();
    }

    private Set<Long> getAccessibleLibraryIds(BookLoreUser user) {
        if (user.getPermissions().isAdmin()) {
            return null;
        }
        if (user.getAssignedLibraries() == null || user.getAssignedLibraries().isEmpty()) {
            return Collections.emptySet();
        }
        return user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());
    }

    private Map<Long, UserBookProgressEntity> getProgressMap(Long userId, Set<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userBookProgressRepository.findByUserIdAndBookIdIn(userId, bookIds).stream()
                .collect(Collectors.toMap(
                        p -> p.getBook().getId(),
                        Function.identity()
                ));
    }

    private Condition buildCondition(
            Set<Long> accessibleLibraryIds,
            Long libraryId,
            Long shelfId,
            ReadStatus status,
            String search,
            Long userId,
            BookFileType fileType,
            Integer minRating,
            Integer maxRating,
            String authors,
            String language) {

        Condition condition = AppBookConditions.notDeleted()
                .and(AppBookConditions.hasDigitalFile());

        if (accessibleLibraryIds != null) {
            if (libraryId != null && accessibleLibraryIds.contains(libraryId)) {
                condition = condition.and(AppBookConditions.inLibrary(libraryId));
            } else if (libraryId != null) {
                throw ApiError.FORBIDDEN.createException("Access denied to library " + libraryId);
            } else {
                condition = condition.and(AppBookConditions.inLibraries(accessibleLibraryIds));
            }
        } else if (libraryId != null) {
            condition = condition.and(AppBookConditions.inLibrary(libraryId));
        }

        if (shelfId != null) {
            ShelfEntity shelf = shelfRepository.findById(shelfId)
                    .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
            if (!shelf.isPublic() && !shelf.getUser().getId().equals(userId)) {
                throw ApiError.FORBIDDEN.createException("Access denied to shelf " + shelfId);
            }
            condition = condition.and(AppBookConditions.inShelf(shelfId));
        }

        if (status != null) {
            condition = condition.and(AppBookConditions.withReadStatus(status.name(), userId));
        }

        if (search != null && !search.trim().isEmpty()) {
            condition = condition.and(AppBookConditions.searchText(search));
        }

        if (fileType != null) {
            condition = condition.and(AppBookConditions.withFileType(fileType.name()));
        }

        if (minRating != null) {
            condition = condition.and(AppBookConditions.withMinRating(minRating, userId));
        }

        if (maxRating != null) {
            condition = condition.and(AppBookConditions.withMaxRating(maxRating, userId));
        }

        if (authors != null && !authors.trim().isEmpty()) {
            condition = condition.and(AppBookConditions.withAuthor(authors.trim()));
        }

        if (language != null && !language.trim().isEmpty()) {
            condition = condition.and(AppBookConditions.withLanguage(language.trim()));
        }

        return condition;
    }

    private Sort buildSort(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String field = switch (sortBy != null ? sortBy.toLowerCase() : "") {
            case "title" -> "metadata.title";
            case "seriesname", "series" -> "metadata.seriesName";
            case "lastreadtime" -> "addedOn";
            default -> "addedOn";
        };

        return Sort.by(direction, field);
    }

    private int validatePageNumber(Integer page) {
        return page != null && page >= 0 ? page : 0;
    }

    private int validatePageSize(Integer size) {
        return size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    }

    private int validateLimit(Integer limit, int defaultValue) {
        return limit != null && limit > 0 ? Math.min(limit, MAX_PAGE_SIZE) : defaultValue;
    }

    private Condition buildBaseCondition(Set<Long> accessibleLibraryIds, Long libraryId) {
        Condition condition = AppBookConditions.notDeleted()
                .and(AppBookConditions.hasDigitalFile());

        if (accessibleLibraryIds != null) {
            if (libraryId != null && !accessibleLibraryIds.contains(libraryId)) {
                throw ApiError.FORBIDDEN.createException("Access denied to library " + libraryId);
            }
            condition = condition.and(libraryId != null
                    ? AppBookConditions.inLibrary(libraryId)
                    : AppBookConditions.inLibraries(accessibleLibraryIds));
        } else if (libraryId != null) {
            condition = condition.and(AppBookConditions.inLibrary(libraryId));
        }

        return condition;
    }

    private AppPageResponse<AppBookSummary> buildPageResponse(
            Page<Long> idPage,
            Long userId,
            int pageNum,
            int pageSize) {

        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, idPage.getTotalElements());
        }

        List<BookEntity> enriched = bookRepository.findAllForSummaryByIds(ids);
        Map<Long, BookEntity> enrichedMap = enriched.stream()
                .collect(Collectors.toMap(BookEntity::getId, b -> b));
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, enriched);

        List<AppBookSummary> summaries = ids.stream()
                .filter(enrichedMap::containsKey)
                .map(id -> mobileBookMapper.toSummary(enrichedMap.get(id), progressMap.get(id)))
                .collect(Collectors.toList());

        return AppPageResponse.of(summaries, pageNum, pageSize, idPage.getTotalElements());
    }

    private Map<Long, UserBookProgressEntity> getProgressMapForBooks(Long userId, List<BookEntity> books) {
        if (books.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> bookIds = books.stream()
                .map(BookEntity::getId)
                .collect(Collectors.toSet());
        return getProgressMap(userId, bookIds);
    }
}
