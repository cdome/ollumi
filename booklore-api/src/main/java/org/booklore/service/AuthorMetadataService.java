package org.booklore.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.AuthorDetails;
import org.booklore.model.dto.AuthorSearchResult;
import org.booklore.model.dto.AuthorSummary;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.AuthorMatchRequest;
import org.booklore.model.dto.request.AuthorUpdateRequest;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.AuthorMetadataSource;
import org.booklore.repository.jooq.JooqAuthorRepository;
import org.booklore.repository.jooq.JooqBookMetadataRelationsRepository;
import org.booklore.repository.jooq.dto.AuthorRow;
import org.booklore.repository.jooq.dto.AuthorSummaryRow;
import org.booklore.service.audit.AuditService;
import org.booklore.service.metadata.DuckDuckGoCoverService;
import org.booklore.service.metadata.parser.AuthorParser;
import org.booklore.util.FileService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class AuthorMetadataService {

    private final JooqAuthorRepository jooqAuthorRepository;
    private final JooqBookMetadataRelationsRepository relationsRepository;
    private final Map<AuthorMetadataSource, AuthorParser> authorParserMap;
    private final AuditService auditService;
    private final FileService fileService;
    private final DuckDuckGoCoverService duckDuckGoCoverService;
    private final AuthenticationService authenticationService;

    public List<AuthorSummary> getAllAuthors() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        List<AuthorSummaryRow> results;
        if (user.getPermissions().isAdmin()) {
            results = jooqAuthorRepository.findAllWithBookCount();
        } else {
            Set<Long> libraryIds = user.getAssignedLibraries().stream()
                    .map(Library::getId)
                    .collect(Collectors.toSet());
            results = jooqAuthorRepository.findAllWithBookCountByLibraryIds(libraryIds);
        }
        List<AuthorSummary> summaries = new ArrayList<>();
        for (AuthorSummaryRow row : results) {
            summaries.add(AuthorSummary.builder()
                    .id(row.getId())
                    .name(row.getName())
                    .asin(row.getAsin())
                    .bookCount((int) row.getBookCount())
                    .hasPhoto(Files.exists(Paths.get(fileService.getAuthorThumbnailFile(row.getId()))))
                    .build());
        }
        return summaries;
    }

    public List<AuthorSearchResult> searchAuthorMetadata(String name, String region) {
        return authorParserMap.values().stream()
                .flatMap(provider -> {
                    List<AuthorSearchResult> results = provider.searchAuthors(name, region);
                    return results != null ? results.stream() : java.util.stream.Stream.empty();
                })
                .toList();
    }

    public List<AuthorSearchResult> lookupAuthorByAsin(String asin, String region) {
        return authorParserMap.values().stream()
                .map(provider -> provider.getAuthorByAsin(asin, region))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public AuthorDetails matchAuthor(Long authorId, AuthorMatchRequest request) {
        AuthorRow author = jooqAuthorRepository.findById(authorId)
                .orElseThrow(() -> ApiError.AUTHOR_NOT_FOUND.createException(authorId));

        AuthorParser provider = request.getSource() != null ? authorParserMap.get(request.getSource()) : null;
        if (provider == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Unsupported author metadata source: " + request.getSource());
        }

        AuthorSearchResult result = provider.getAuthorByAsin(request.getAsin(), request.getRegion());
        if (result == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Failed to fetch author metadata");
        }

        applyMetadataResult(author, result);
        jooqAuthorRepository.save(author);

        if (!author.getPhotoLocked() && result.getImageUrl() != null && !result.getImageUrl().isBlank()) {
            fileService.createAuthorThumbnailFromUrl(author.getId(), result.getImageUrl());
        }

        auditService.log(AuditAction.AUTHOR_METADATA_UPDATED, "Author", authorId,
                "Matched author '" + author.getName() + "' via " + result.getSource() + " (ASIN: " + result.getAsin() + ")");

        return toAuthorDetails(author);
    }

    public AuthorDetails quickMatchAuthor(Long authorId, String region) {
        AuthorRow author = jooqAuthorRepository.findById(authorId)
                .orElseThrow(() -> ApiError.AUTHOR_NOT_FOUND.createException(authorId));

        for (AuthorParser provider : authorParserMap.values()) {
            AuthorSearchResult result = provider.quickSearch(author.getName(), region);
            if (result != null) {
                applyMetadataResult(author, result);
                jooqAuthorRepository.save(author);

                if (!author.getPhotoLocked() && result.getImageUrl() != null && !result.getImageUrl().isBlank()) {
                    fileService.createAuthorThumbnailFromUrl(author.getId(), result.getImageUrl());
                }

                auditService.log(AuditAction.AUTHOR_METADATA_UPDATED, "Author", authorId,
                        "Quick-matched author '" + author.getName() + "' via " + result.getSource() + " (ASIN: " + result.getAsin() + ")");

                return toAuthorDetails(author);
            }
        }

        throw ApiError.GENERIC_BAD_REQUEST.createException("No metadata found for author: " + author.getName());
    }

    public Flux<AuthorSummary> autoMatchAuthors(List<Long> authorIds) {
        return Flux.fromIterable(authorIds)
                .concatMap(authorId ->
                        Mono.fromCallable(() -> {
                            AuthorRow author = jooqAuthorRepository.findById(authorId).orElse(null);
                            if (author == null) return null;
                            AuthorDetails details = quickMatchAuthor(authorId, "us");
                            return AuthorSummary.builder()
                                    .id(details.getId())
                                    .name(details.getName())
                                    .asin(details.getAsin())
                                    .hasPhoto(Files.exists(Paths.get(fileService.getAuthorThumbnailFile(authorId))))
                                    .build();
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .delayElement(java.time.Duration.ofMillis(
                                java.util.concurrent.ThreadLocalRandom.current().nextLong(250, 750)))
                        .onErrorResume(e -> {
                            log.warn("Failed to auto-match author ID {}: {}", authorId, e.getMessage());
                            return Mono.empty();
                        })
                )
                .filter(java.util.Objects::nonNull);
    }

    public void unmatchAuthors(List<Long> authorIds) {
        for (Long authorId : authorIds) {
            AuthorRow author = jooqAuthorRepository.findById(authorId).orElse(null);
            if (author == null) continue;

            author.setDescription(null);
            author.setAsin(null);
            jooqAuthorRepository.save(author);
            fileService.deleteAuthorImages(authorId);

            auditService.log(AuditAction.AUTHOR_METADATA_UPDATED, "Author", authorId,
                    "Unmatched author '" + author.getName() + "'");
        }
    }

    @Transactional
    public void deleteAuthors(List<Long> authorIds) {
        for (Long authorId : authorIds) {
            AuthorRow author = jooqAuthorRepository.findById(authorId).orElse(null);
            if (author == null) continue;

            String authorName = author.getName();

            List<Long> affectedBookIds = relationsRepository.findBookIdsByAuthorId(authorId);
            for (Long bookId : affectedBookIds) {
                List<String> remainingAuthors = new ArrayList<>(relationsRepository.findAuthorNamesByBookId(bookId));
                remainingAuthors.remove(authorName);
                relationsRepository.setAuthorsForBook(bookId, remainingAuthors);
            }

            fileService.deleteAuthorImages(authorId);
            jooqAuthorRepository.deleteById(authorId);

            auditService.log(AuditAction.AUTHOR_DELETED, "Author", authorId,
                    "Deleted author '" + authorName + "'");
        }
    }

    public void uploadAuthorPhoto(Long authorId, MultipartFile file) {
        jooqAuthorRepository.findById(authorId)
                .orElseThrow(() -> ApiError.AUTHOR_NOT_FOUND.createException(authorId));

        try {
            java.awt.image.BufferedImage image = FileService.readImage(file.getInputStream());
            if (image == null) {
                throw ApiError.FILE_READ_ERROR.createException("Failed to decode image");
            }
            fileService.saveAuthorImages(image, authorId);
            image.flush();
        } catch (java.io.IOException e) {
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        }
    }

    public AuthorDetails updateAuthor(Long authorId, AuthorUpdateRequest request) {
        AuthorRow author = jooqAuthorRepository.findById(authorId)
                .orElseThrow(() -> ApiError.AUTHOR_NOT_FOUND.createException(authorId));

        if (request.getName() != null) {
            author.setName(request.getName());
        }
        if (request.getDescription() != null) {
            author.setDescription(request.getDescription().isBlank() ? null : request.getDescription());
        }
        if (request.getAsin() != null) {
            author.setAsin(request.getAsin().isBlank() ? null : request.getAsin());
        }
        if (request.getNameLocked() != null) {
            author.setNameLocked(request.getNameLocked());
        }
        if (request.getDescriptionLocked() != null) {
            author.setDescriptionLocked(request.getDescriptionLocked());
        }
        if (request.getAsinLocked() != null) {
            author.setAsinLocked(request.getAsinLocked());
        }
        if (request.getPhotoLocked() != null) {
            author.setPhotoLocked(request.getPhotoLocked());
        }

        jooqAuthorRepository.save(author);

        auditService.log(AuditAction.AUTHOR_METADATA_UPDATED, "Author", authorId,
                "Updated author '" + author.getName() + "'");

        return toAuthorDetails(author);
    }

    public List<CoverImage> searchAuthorPhotos(String name) {
        String searchTerm = name + " author photo portrait";
        List<CoverImage> results = duckDuckGoCoverService.searchImages(searchTerm);
        if (results.size() > 50) {
            results = results.subList(0, 50);
        }
        return results;
    }

    public void uploadAuthorPhotoFromUrl(Long authorId, String imageUrl) {
        jooqAuthorRepository.findById(authorId)
                .orElseThrow(() -> ApiError.AUTHOR_NOT_FOUND.createException(authorId));

        fileService.createAuthorThumbnailFromUrl(authorId, imageUrl);
    }

    public AuthorDetails getAuthorByName(String name) {
        AuthorRow author = jooqAuthorRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> ApiError.AUTHOR_NOT_FOUND.createException(name));
        verifyAuthorAccess(author.getId());
        return toAuthorDetails(author);
    }

    public AuthorDetails getAuthorDetails(Long authorId) {
        AuthorRow author = jooqAuthorRepository.findById(authorId)
                .orElseThrow(() -> ApiError.AUTHOR_NOT_FOUND.createException(authorId));
        verifyAuthorAccess(authorId);
        return toAuthorDetails(author);
    }

    public Resource getAuthorPhoto(Long authorId) {
        Path photoPath = Paths.get(fileService.getAuthorPhotoFile(authorId));
        try {
            if (Files.exists(photoPath)) {
                return new UrlResource(photoPath.toUri());
            }
        } catch (MalformedURLException e) {
            log.warn("Malformed URL for author photo path: {}", photoPath);
        }
        return null;
    }

    public Resource getAuthorThumbnail(Long authorId) {
        Path thumbnailPath = Paths.get(fileService.getAuthorThumbnailFile(authorId));
        try {
            if (Files.exists(thumbnailPath)) {
                return new UrlResource(thumbnailPath.toUri());
            }
        } catch (MalformedURLException e) {
            log.warn("Malformed URL for author thumbnail path: {}", thumbnailPath);
        }
        return null;
    }

    private void verifyAuthorAccess(Long authorId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        if (user.getPermissions().isAdmin()) {
            return;
        }
        Set<Long> libraryIds = user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());
        if (libraryIds.isEmpty() || !jooqAuthorRepository.existsByIdAndLibraryIds(authorId, libraryIds)) {
            throw ApiError.AUTHOR_NOT_FOUND.createException(authorId);
        }
    }

    private void applyMetadataResult(AuthorRow author, AuthorSearchResult result) {
        if (!author.getDescriptionLocked()) {
            author.setDescription(result.getDescription());
        }
        if (!author.getAsinLocked()) {
            author.setAsin(result.getAsin());
        }
    }

    private AuthorDetails toAuthorDetails(AuthorRow author) {
        return AuthorDetails.builder()
                .id(author.getId())
                .name(author.getName())
                .description(author.getDescription())
                .asin(author.getAsin())
                .nameLocked(author.getNameLocked())
                .descriptionLocked(author.getDescriptionLocked())
                .asinLocked(author.getAsinLocked())
                .photoLocked(author.getPhotoLocked())
                .build();
    }
}
