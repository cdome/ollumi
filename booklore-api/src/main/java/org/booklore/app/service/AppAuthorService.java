package org.booklore.app.service;

import lombok.AllArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.app.dto.AppAuthorDetail;
import org.booklore.app.dto.AppAuthorSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.repository.AuthorRepository;
import org.booklore.repository.jooq.JooqAppAuthorRepository;
import org.booklore.repository.jooq.dto.AuthorSummaryRow;
import org.booklore.util.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AppAuthorService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 50;

    private final AuthorRepository authorRepository;
    private final JooqAppAuthorRepository jooqAppAuthorRepository;
    private final AuthenticationService authenticationService;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public AppPageResponse<AppAuthorSummary> getAuthors(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Long libraryId,
            String search,
            Boolean hasPhoto) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        long totalElements = jooqAppAuthorRepository.countAuthors(accessibleLibraryIds, libraryId, search);
        if (totalElements == 0) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        List<AuthorSummaryRow> rows = jooqAppAuthorRepository.findAuthorSummaries(
                accessibleLibraryIds, libraryId, search, sortBy, sortDir, pageNum * pageSize, pageSize);

        List<AppAuthorSummary> summaries = rows.stream()
                .map(row -> AppAuthorSummary.builder()
                        .id(row.getId())
                        .name(row.getName())
                        .asin(row.getAsin())
                        .bookCount((int) row.getBookCount())
                        .hasPhoto(authorHasPhoto(row.getId()))
                        .build())
                .collect(Collectors.toList());

        // Post-filter by hasPhoto if requested
        if (hasPhoto != null) {
            summaries = summaries.stream()
                    .filter(s -> s.isHasPhoto() == hasPhoto)
                    .collect(Collectors.toList());
            // Adjust total count for hasPhoto filter — requires a separate count
            long filteredTotal = countAuthorsWithPhotoFilter(accessibleLibraryIds, libraryId, search, hasPhoto);
            return AppPageResponse.of(summaries, pageNum, pageSize, filteredTotal);
        }

        return AppPageResponse.of(summaries, pageNum, pageSize, totalElements);
    }

    @Transactional(readOnly = true)
    public AppAuthorDetail getAuthorDetail(Long authorId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        AuthorEntity author = authorRepository.findById(authorId)
                .orElseThrow(() -> ApiError.AUTHOR_NOT_FOUND.createException(authorId));

        // Verify access for non-admin users
        if (accessibleLibraryIds != null) {
            if (accessibleLibraryIds.isEmpty() || !authorRepository.existsByIdAndLibraryIds(authorId, accessibleLibraryIds)) {
                throw ApiError.AUTHOR_NOT_FOUND.createException(authorId);
            }
        }

        int bookCount = jooqAppAuthorRepository.countAccessibleBooks(authorId, accessibleLibraryIds);
        boolean hasPhoto = authorHasPhoto(author.getId());

        return AppAuthorDetail.builder()
                .id(author.getId())
                .name(author.getName())
                .description(author.getDescription())
                .asin(author.getAsin())
                .bookCount(bookCount)
                .hasPhoto(hasPhoto)
                .build();
    }

    private long countAuthorsWithPhotoFilter(Set<Long> accessibleLibraryIds, Long libraryId, String search, boolean hasPhoto) {
        // hasPhoto is filesystem-based, so count matching authors and check their photos.
        return jooqAppAuthorRepository.findMatchingAuthorIds(accessibleLibraryIds, libraryId, search).stream()
                .filter(id -> authorHasPhoto(id) == hasPhoto)
                .count();
    }

    private boolean authorHasPhoto(Long authorId) {
        return Files.exists(Paths.get(fileService.getAuthorThumbnailFile(authorId)));
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
}
