package org.booklore.service.library;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.LibraryMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.LibraryPath;
import org.booklore.model.dto.request.CreateLibraryRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.LibraryPathRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.repository.jooq.JooqLibraryPathRepository;
import org.booklore.repository.jooq.JooqUserLibraryRepository;
import org.booklore.repository.jooq.dto.LibraryPathRow;
import org.booklore.service.NotificationService;
import org.booklore.service.audit.AuditService;
import org.booklore.service.monitoring.LibraryWatchService;
import org.booklore.task.options.RescanLibraryContext;
import org.booklore.util.FileService;
import org.booklore.util.SecurityContextVirtualThread;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.event.EventListener;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
@DependsOnDatabaseInitialization
public class LibraryService {

    private static final Set<Long> scanningLibraries = ConcurrentHashMap.newKeySet();

    /**
     * Checks whether a library is currently being scanned.
     * Can be used by other components (e.g., file watcher) to avoid processing
     * files while a full scan is in progress.
     */
    public static boolean isLibraryScanning(long libraryId) {
        return scanningLibraries.contains(libraryId);
    }

    private final LibraryRepository libraryRepository;
    private final LibraryPathRepository libraryPathRepository;
    private final JooqLibraryPathRepository jooqLibraryPathRepository;
    private final JooqUserLibraryRepository jooqUserLibraryRepository;
    private final JooqBookEntityRepository jooqBookEntityRepository;
    private final JooqBookRepository jooqBookRepository;
    private final LibraryProcessingService libraryProcessingService;
    private final BookMapper bookMapper;
    private final LibraryMapper libraryMapper;
    private final NotificationService notificationService;
    private final FileService fileService;
    private final LibraryWatchService libraryWatchService;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void initializeMonitoring() {
        List<Library> libraries = libraryRepository.findAll().stream().map(libraryMapper::toLibrary).collect(Collectors.toList());
        libraryWatchService.registerLibraries(libraries);
        log.info("Monitoring initialized with {} libraries", libraries.size());
    }

    @Transactional
    public Library updateLibrary(CreateLibraryRequest request, Long libraryId) {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        library.setName(request.getName());
        library.setIcon(request.getIcon());
        library.setIconType(request.getIconType());
        library.setWatch(request.isWatch());
        library.setFormatPriority(request.getFormatPriority());
        library.setAllowedFormats(request.getAllowedFormats());
        if (request.getMetadataSource() != null) {
            library.setMetadataSource(request.getMetadataSource());
        }
        if (request.getOrganizationMode() != null) {
            library.setOrganizationMode(request.getOrganizationMode());
        }

        List<LibraryPathRow> currentPathRows = jooqLibraryPathRepository.findPathsByLibraryId(libraryId);
        Set<String> currentPaths = currentPathRows.stream()
                .map(LibraryPathRow::getPath)
                .collect(Collectors.toSet());
        Set<String> updatedPaths = request.getPaths().stream()
                .map(LibraryPath::getPath)
                .collect(Collectors.toSet());

        Set<String> deletedPaths = currentPaths.stream()
                .filter(path -> !updatedPaths.contains(path))
                .collect(Collectors.toSet());
        Set<String> newPaths = updatedPaths.stream()
                .filter(path -> !currentPaths.contains(path))
                .collect(Collectors.toSet());

        if (!deletedPaths.isEmpty()) {
            Set<Long> idsToRemove = currentPathRows.stream()
                    .filter(row -> deletedPaths.contains(row.getPath()))
                    .map(LibraryPathRow::getId)
                    .collect(Collectors.toSet());

            List<Long> books = jooqBookRepository.findBookIdsByLibraryPathIds(idsToRemove);

            if (!books.isEmpty()) {
                notificationService.sendMessage(Topic.BOOKS_REMOVE, books);
            }

            libraryPathRepository.deleteAllById(idsToRemove);
        }

        if (!newPaths.isEmpty()) {
            List<LibraryPathEntity> newPathEntities = newPaths.stream()
                    .map(path -> LibraryPathEntity.builder().path(path).library(library).build())
                    .collect(Collectors.toList());

            libraryPathRepository.saveAll(newPathEntities);
        }

        LibraryEntity savedLibrary = libraryRepository.save(library);

        if (request.isWatch()) {
            libraryWatchService.registerLibraries(List.of(libraryMapper.toLibrary(savedLibrary)));
        } else {
            libraryWatchService.unregisterLibrary(libraryId);
        }

        if (!newPaths.isEmpty()) {
            scheduleBackgroundScanAfterCommit(libraryId);
        }

        auditService.log(AuditAction.LIBRARY_UPDATED, "Library", libraryId, "Updated library: " + library.getName());
        return libraryMapper.toLibrary(savedLibrary);
    }

    @Transactional
    public Library createLibrary(CreateLibraryRequest request) {
        LibraryEntity libraryEntity = LibraryEntity.builder()
                .name(request.getName())
                .icon(request.getIcon())
                .iconType(request.getIconType())
                .watch(request.isWatch())
                .formatPriority(request.getFormatPriority())
                .allowedFormats(request.getAllowedFormats())
                .metadataSource(request.getMetadataSource())
                .organizationMode(request.getOrganizationMode())
                .build();

        final LibraryEntity savedLibraryEntity = libraryRepository.save(libraryEntity);
        libraryEntity = savedLibraryEntity;
        Long libraryId = libraryEntity.getId();

        List<LibraryPathEntity> pathEntities = request.getPaths() == null || request.getPaths().isEmpty() ?
                Collections.emptyList() :
                request.getPaths().stream()
                        .map(path -> LibraryPathEntity.builder().path(path.getPath()).library(savedLibraryEntity).build())
                        .collect(Collectors.toList());

        if (!pathEntities.isEmpty()) {
            libraryPathRepository.saveAll(pathEntities);
        }

        if (request.isWatch()) {
            for (LibraryPathEntity pathEntity : pathEntities) {
                Path path = Paths.get(pathEntity.getPath());
                libraryWatchService.registerPath(path, libraryId);
            }
        }

        scheduleBackgroundScanAfterCommit(libraryId);

        auditService.log(AuditAction.LIBRARY_CREATED, "Library", libraryEntity.getId(), "Created library: " + libraryEntity.getName());
        return libraryMapper.toLibrary(libraryEntity);
    }

    public void rescanLibrary(long libraryId) {
        LibraryEntity lib = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        auditService.log(AuditAction.LIBRARY_SCANNED, "Library", libraryId, "Scanned library: " + lib.getName());

        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            if (!scanningLibraries.add(libraryId)) {
                log.warn("Library {} is already being scanned, skipping duplicate rescan request", libraryId);
                return;
            }
            try {
                RescanLibraryContext context = RescanLibraryContext.builder()
                        .libraryId(libraryId)
                        .build();
                libraryProcessingService.rescanLibrary(context);
            } catch (InvalidDataAccessApiUsageException e) {
                log.debug("InvalidDataAccessApiUsageException - Library id: {}", libraryId);
            } catch (IOException e) {
                log.error("Error while parsing library books", e);
            } finally {
                scanningLibraries.remove(libraryId);
            }
            log.info("Parsing task completed!");
        });
    }

    @Transactional(readOnly = true)
    public Library getLibrary(long libraryId) {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        return libraryMapper.toLibrary(libraryEntity);
    }

    @Transactional(readOnly = true)
    public List<Library> getAllLibraries() {
        List<LibraryEntity> libraries = libraryRepository.findAll();
        return libraries.stream().map(libraryMapper::toLibrary).toList();
    }

    @Transactional(readOnly = true)
    public List<Library> getLibraries() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookLoreUserEntity userEntity = userRepository.findByIdWithLibraries(user.getId()).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        List<LibraryEntity> libraries;
        if (userEntity.getPermissions().isPermissionAdmin()) {
            libraries = libraryRepository.findAll();
        } else {
            List<Long> libraryIds = new ArrayList<>(jooqUserLibraryRepository.findLibraryIdsByUserId(user.getId()));
            libraries = libraryRepository.findByIdIn(libraryIds);
        }
        return libraries.stream().map(libraryMapper::toLibrary).toList();
    }

    @Transactional
    public void deleteLibrary(long id) {
        LibraryEntity library = libraryRepository.findById(id)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(id));
        libraryWatchService.unregisterLibrary(id);
        Set<Long> bookIds = jooqBookRepository.findBookIdsByLibraryId(id);
        fileService.deleteBookCovers(bookIds);
        String libraryName = library.getName();
        libraryRepository.deleteById(id);
        auditService.log(AuditAction.LIBRARY_DELETED, "Library", id, "Deleted library: " + libraryName);
        log.info("Library deleted successfully: {}", id);
    }

    @Transactional(readOnly = true)
    public Book getBook(long libraryId, long bookId) {
        libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        BookEntity bookEntity = jooqBookEntityRepository.findByIdFull(bookId);
        if (bookEntity == null || bookEntity.getLibrary().getId() != libraryId) {
            throw ApiError.BOOK_NOT_FOUND.createException(bookId);
        }
        return bookMapper.toBook(bookEntity);
    }

    @Transactional(readOnly = true)
    public List<Book> getBooks(long libraryId) {
        libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        List<BookEntity> bookEntities = jooqBookEntityRepository.findAllWithMetadataByLibraryId(libraryId);
        return bookEntities.stream().map(bookMapper::toBook).toList();
    }

    @Transactional
    public Library setFileNamingPattern(long libraryId, String pattern) {
        LibraryEntity library = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        library.setFileNamingPattern(pattern);
        Library result = libraryMapper.toLibrary(libraryRepository.save(library));
        auditService.log(AuditAction.NAMING_PATTERN_CHANGED, "Library", libraryId, "Changed naming pattern for library: " + library.getName() + " to: " + pattern);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getBookCountsByFormat(long libraryId) {
        libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        Map<String, Long> counts = new HashMap<>();
        for (BookFileType type : BookFileType.values()) {
            long count = jooqBookRepository.countByLibraryIdAndBookType(libraryId, type);
            if (count > 0) {
                counts.put(type.name(), count);
            }
        }
        return counts;
    }

    public int scanLibraryPaths(CreateLibraryRequest request) {
        int count = 0;
        if (request.getPaths() == null || request.getPaths().isEmpty()) {
            return count;
        }
        Set<BookFileType> allowedFormats = request.getAllowedFormats() != null && !request.getAllowedFormats().isEmpty()
                ? Set.copyOf(request.getAllowedFormats())
                : null;
        for (LibraryPath libraryPath : request.getPaths()) {
            Path path = Paths.get(libraryPath.getPath());
            if (!Files.exists(path)) {
                log.warn("Path does not exist: {}", path);
                continue;
            }
            if (Files.isDirectory(path)) {
                count += scanDirectory(path, allowedFormats);
            } else if (Files.isRegularFile(path) && isProcessableFile(path, allowedFormats)) {
                count++;
            }
        }
        return count;
    }

    private int scanDirectory(Path directory, Set<BookFileType> allowedFormats) {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    count += scanDirectory(entry, allowedFormats);
                } else if (Files.isRegularFile(entry) && isProcessableFile(entry, allowedFormats)) {
                    count++;
                }
            }
        } catch (IOException e) {
            log.error("Error scanning directory: {}", directory, e);
        }
        return count;
    }

    private boolean isProcessableFile(Path file, Set<BookFileType> allowedFormats) {
        String fileName = file.getFileName().toString().toLowerCase();
        for (BookFileType fileType : BookFileType.values()) {
            if (allowedFormats != null && !allowedFormats.contains(fileType)) {
                continue;
            }
            for (String ext : fileType.getExtensions()) {
                if (fileName.endsWith("." + ext)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void scheduleBackgroundScanAfterCommit(long libraryId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    startBackgroundScan(libraryId);
                }
            });
        } else {
            startBackgroundScan(libraryId);
        }
    }

    private void startBackgroundScan(long libraryId) {
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            if (!scanningLibraries.add(libraryId)) {
                log.warn("Library {} is already being scanned, skipping duplicate process request", libraryId);
                return;
            }
            try {
                libraryProcessingService.processLibrary(libraryId);
            } catch (InvalidDataAccessApiUsageException e) {
                log.debug("InvalidDataAccessApiUsageException - Library id: {}", libraryId);
            } finally {
                scanningLibraries.remove(libraryId);
            }
            log.info("Parsing task completed!");
        });
    }
}
