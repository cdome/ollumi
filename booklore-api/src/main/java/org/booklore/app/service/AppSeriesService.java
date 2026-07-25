package org.booklore.app.service;

import lombok.AllArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.app.dto.*;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.*;
import org.booklore.repository.BookRepository;
import org.booklore.repository.jooq.AppBookConditions;
import org.booklore.repository.jooq.JooqAppBookRepository;
import org.booklore.repository.jooq.JooqAppBookSummaryRepository;
import org.booklore.repository.jooq.JooqAppSeriesRepository;
import org.booklore.repository.jooq.dto.SeriesAggregate;
import org.jooq.Condition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AppSeriesService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final AuthenticationService authenticationService;
    private final BookRepository bookRepository;
    private final JooqAppBookRepository jooqAppBookRepository;
    private final JooqAppBookSummaryRepository jooqAppBookSummaryRepository;
    private final JooqAppSeriesRepository jooqAppSeriesRepository;

    @Transactional(readOnly = true)
    public AppPageResponse<AppSeriesSummary> getSeries(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Long libraryId,
            String search,
            boolean inProgressOnly) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        if (libraryId != null) {
            validateLibraryAccess(accessibleLibraryIds, libraryId);
        }

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        List<SeriesAggregate> aggregates = jooqAppSeriesRepository.findSeriesAggregates(
                userId, accessibleLibraryIds, libraryId, search, inProgressOnly,
                sortBy, sortDir, pageNum * pageSize, pageSize);

        long totalElements = jooqAppSeriesRepository.countSeries(
                userId, accessibleLibraryIds, libraryId, search, inProgressOnly);

        return buildSeriesPage(aggregates, accessibleLibraryIds, libraryId, pageNum, pageSize, totalElements);
    }

    private AppPageResponse<AppSeriesSummary> buildSeriesPage(
            List<SeriesAggregate> aggregates,
            Set<Long> accessibleLibraryIds,
            Long libraryId,
            int pageNum,
            int pageSize,
            long totalElements) {

        if (aggregates.isEmpty()) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, totalElements);
        }

        List<String> seriesNames = aggregates.stream()
                .map(SeriesAggregate::getSeriesName)
                .toList();

        // Phase 2: Fetch books for enrichment via the two-query pattern
        List<Long> bookIds = jooqAppSeriesRepository.findBookIdsBySeriesNames(
                seriesNames, accessibleLibraryIds, libraryId);
        List<BookEntity> books = bookRepository.findAllForSummaryByIds(bookIds);

        Map<String, List<BookEntity>> booksBySeries = books.stream()
                .filter(b -> b.getMetadata() != null && b.getMetadata().getSeriesName() != null)
                .collect(Collectors.groupingBy(b -> b.getMetadata().getSeriesName()));

        // Merge into summaries, preserving Phase 1 order
        List<AppSeriesSummary> summaries = new ArrayList<>();
        for (SeriesAggregate agg : aggregates) {
            List<BookEntity> seriesBooks = booksBySeries.getOrDefault(agg.getSeriesName(), Collections.emptyList());

            // Distinct authors across all books in series
            List<String> authors = seriesBooks.stream()
                    .filter(b -> b.getMetadata() != null && b.getMetadata().getAuthors() != null)
                    .flatMap(b -> b.getMetadata().getAuthors().stream())
                    .map(AuthorEntity::getName)
                    .distinct()
                    .toList();

            // Cover books sorted by seriesNumber ASC nulls last
            List<SeriesCoverBook> coverBooks = seriesBooks.stream()
                    .sorted(Comparator.comparing(
                            (BookEntity b) -> b.getMetadata().getSeriesNumber(),
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(b -> {
                        BookFileEntity primaryFile = b.getPrimaryBookFile();
                        String fileType = (primaryFile != null && primaryFile.getBookType() != null)
                                ? primaryFile.getBookType().name()
                                : null;
                        return SeriesCoverBook.builder()
                                .bookId(b.getId())
                                .coverUpdatedOn(b.getMetadata().getCoverUpdatedOn())
                                .seriesNumber(b.getMetadata().getSeriesNumber())
                                .primaryFileType(fileType)
                                .build();
                    })
                    .toList();

            summaries.add(AppSeriesSummary.builder()
                    .seriesName(agg.getSeriesName())
                    .bookCount((int) agg.getBookCount())
                    .seriesTotal(agg.getSeriesTotal())
                    .latestAddedOn(agg.getLatestAddedOn() != null
                            ? agg.getLatestAddedOn().toInstant(ZoneOffset.UTC)
                            : null)
                    .booksRead((int) agg.getBooksRead())
                    .authors(authors)
                    .coverBooks(coverBooks)
                    .build());
        }

        return AppPageResponse.of(summaries, pageNum, pageSize, totalElements);
    }

    @Transactional(readOnly = true)
    public AppPageResponse<AppBookSummary> getSeriesBooks(
            String seriesName,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Long libraryId) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        if (libraryId != null) {
            validateLibraryAccess(accessibleLibraryIds, libraryId);
        }

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Sort sort = buildBookSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        Condition condition = buildSeriesBooksCondition(accessibleLibraryIds, libraryId, seriesName);

        Page<Long> idPage = jooqAppBookRepository.findBookIds(condition, pageable);
        List<Long> ids = idPage.getContent();

        if (ids.isEmpty()) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, idPage.getTotalElements());
        }

        Map<Long, AppBookSummary> byId = jooqAppBookSummaryRepository.findSummariesByIds(ids, userId).stream()
                .collect(Collectors.toMap(AppBookSummary::getId, s -> s));

        List<AppBookSummary> summaries = ids.stream()
                .filter(byId::containsKey)
                .map(byId::get)
                .toList();

        return AppPageResponse.of(summaries, pageNum, pageSize, idPage.getTotalElements());
    }

    // --- Access control helpers (duplicated from AppBookService to minimize blast radius) ---

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

    private void validateLibraryAccess(Set<Long> accessibleLibraryIds, Long libraryId) {
        if (accessibleLibraryIds != null && !accessibleLibraryIds.contains(libraryId)) {
            throw ApiError.FORBIDDEN.createException("Access denied to library " + libraryId);
        }
    }

    // --- Query helpers ---

    private Sort buildBookSort(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = switch (sortBy != null ? sortBy.toLowerCase() : "") {
            case "title" -> "metadata.title";
            case "seriesnumber" -> "metadata.seriesNumber";
            case "recentlyadded" -> "addedOn";
            default -> "metadata.seriesNumber";
        };
        return Sort.by(direction, field);
    }

    private Condition buildSeriesBooksCondition(Set<Long> accessibleLibraryIds, Long libraryId, String seriesName) {
        Condition condition = AppBookConditions.notDeleted()
                .and(AppBookConditions.hasDigitalFile())
                .and(AppBookConditions.inSeries(seriesName));

        if (accessibleLibraryIds != null) {
            condition = condition.and(libraryId != null
                    ? AppBookConditions.inLibrary(libraryId)
                    : AppBookConditions.inLibraries(accessibleLibraryIds));
        } else if (libraryId != null) {
            condition = condition.and(AppBookConditions.inLibrary(libraryId));
        }

        return condition;
    }
}
