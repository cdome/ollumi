package org.booklore.app.controller;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.app.dto.AppLibrarySummary;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.repository.jooq.JooqLibraryPathRepository;
import org.booklore.repository.jooq.dto.LibraryPathRow;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/libraries")
public class AppLibraryController {

    private final AuthenticationService authenticationService;
    private final LibraryRepository libraryRepository;
    private final JooqBookRepository jooqBookRepository;
    private final JooqLibraryPathRepository jooqLibraryPathRepository;
    private final AppBookMapper mobileBookMapper;

    @GetMapping
    public ResponseEntity<List<AppLibrarySummary>> getLibraries() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        List<LibraryEntity> libraries;
        if (user.getPermissions().isAdmin()) {
            libraries = libraryRepository.findAll();
        } else {
            List<Long> libraryIds = user.getAssignedLibraries() != null
                    ? user.getAssignedLibraries().stream().map(Library::getId).collect(Collectors.toList())
                    : List.of();
            libraries = libraryRepository.findByIdIn(libraryIds);
        }

        Map<Long, List<LibraryPathRow>> pathsByLibrary = jooqLibraryPathRepository.findPathsByLibraryIds(
                libraries.stream().map(LibraryEntity::getId).collect(Collectors.toList()));

        List<AppLibrarySummary> summaries = libraries.stream()
                .map(library -> {
                    long bookCount = jooqBookRepository.countByLibraryId(library.getId());
                    return mobileBookMapper.toLibrarySummary(library, bookCount,
                            pathsByLibrary.getOrDefault(library.getId(), List.of()));
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }
}
