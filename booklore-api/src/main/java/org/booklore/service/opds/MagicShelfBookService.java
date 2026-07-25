package org.booklore.service.opds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.GroupRule;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.MagicShelfEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.MagicShelfRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqBookRuleEvaluator;
import org.booklore.repository.jooq.JooqMagicShelfBookRepository;
import org.booklore.service.restriction.ContentRestrictionService;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.booklore.jooq.tables.Book.BOOK;

@Slf4j
@RequiredArgsConstructor
@Service
public class MagicShelfBookService {

    private final MagicShelfRepository magicShelfRepository;
    private final BookRepository bookRepository;
    private final JooqMagicShelfBookRepository jooqMagicShelfBookRepository;
    private final BookMapper bookMapper;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final JooqBookRuleEvaluator ruleEvaluator;
    private final ContentRestrictionService contentRestrictionService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<Book> getBooksByMagicShelfId(Long userId, Long magicShelfId, int page, int size) {
        MagicShelfEntity shelf = validateMagicShelfAccess(userId, magicShelfId);
        try {
            Condition condition = buildShelfCondition(shelf, userId);
            Pageable pageable = PageRequest.of(Math.max(page, 0), size);

            Page<Long> idPage = jooqMagicShelfBookRepository.findBookIds(condition, pageable);
            List<BookEntity> entities = loadBooksPreservingOrder(idPage.getContent());

            List<BookEntity> filteredEntities = contentRestrictionService.applyRestrictions(entities, userId);
            List<Book> books = filteredEntities.stream()
                    .map(bookMapper::toBook)
                    .map(book -> filterBook(book, userId))
                    .toList();
            return new PageImpl<>(books, pageable, idPage.getTotalElements());
        } catch (Exception e) {
            log.error("Failed to parse or execute magic shelf rules", e);
            throw new RuntimeException("Failed to parse or execute magic shelf rules: " + e.getMessage(), e);
        }
    }

    public List<Long> getBookIdsByMagicShelfId(Long userId, Long magicShelfId) {
        return getBookIdsByMagicShelfId(userId, magicShelfId, Integer.MAX_VALUE);
    }

    public List<Long> getBookIdsByMagicShelfId(Long userId, Long magicShelfId, int limit) {
        MagicShelfEntity shelf = validateMagicShelfAccess(userId, magicShelfId);
        try {
            Condition condition = buildShelfCondition(shelf, userId);

            Page<Long> idPage = jooqMagicShelfBookRepository.findBookIds(condition, PageRequest.of(0, limit));
            List<BookEntity> entities = loadBooksPreservingOrder(idPage.getContent());
            List<BookEntity> filtered = contentRestrictionService.applyRestrictions(entities, userId);
            return filtered.stream().map(BookEntity::getId).toList();
        } catch (Exception e) {
            log.error("Failed to parse or execute magic shelf rules", e);
            throw new RuntimeException("Failed to parse or execute magic shelf rules: " + e.getMessage(), e);
        }
    }

    public String getMagicShelfName(Long magicShelfId) {
        return magicShelfRepository.findById(magicShelfId)
                .map(s -> s.getName() + " - Magic Shelf")
                .orElse("Magic Shelf Books");
    }

    private Condition buildShelfCondition(MagicShelfEntity shelf, Long userId) {
        GroupRule groupRule = objectMapper.readValue(shelf.getFilterJson(), GroupRule.class);
        return ruleEvaluator.toCondition(groupRule, userId)
                .and(libraryFilterCondition(userId));
    }

    private MagicShelfEntity validateMagicShelfAccess(Long userId, Long magicShelfId) {
        MagicShelfEntity shelf = magicShelfRepository.findById(magicShelfId)
                .orElseThrow(() -> ApiError.MAGIC_SHELF_NOT_FOUND.createException(magicShelfId));

        if (userId == null) {
            if (!shelf.isPublic()) {
                throw ApiError.FORBIDDEN.createException("You are not allowed to access this magic shelf");
            }
            return shelf;
        }

        BookLoreUserEntity entity = userRepository.findByIdWithDetails(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        if (entity.getPermissions() == null ||
                (!entity.getPermissions().isPermissionAccessOpds() && !entity.getPermissions().isPermissionAdmin())) {
            throw ApiError.FORBIDDEN.createException("You are not allowed to access this resource");
        }

        boolean isOwner = shelf.getUserId().equals(userId);
        boolean isPublic = shelf.isPublic();
        boolean isAdmin = entity.getPermissions().isPermissionAdmin();

        if (!isOwner && !isPublic && !isAdmin) {
            throw ApiError.FORBIDDEN.createException("You are not allowed to access this magic shelf");
        }

        return shelf;
    }

    private Condition libraryFilterCondition(Long userId) {
        BookLoreUserEntity entity = userRepository.findByIdWithDetails(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);

        if (user.getPermissions() != null && user.getPermissions().isAdmin()) {
            return DSL.noCondition();
        }

        Set<Long> userLibraryIds = user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());

        return BOOK.LIBRARY_ID.in(userLibraryIds);
    }

    private List<BookEntity> loadBooksPreservingOrder(List<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return List.of();
        }
        Map<Long, BookEntity> booksById = bookRepository.findAllWithMetadataByIds(new HashSet<>(bookIds)).stream()
                .collect(Collectors.toMap(BookEntity::getId, book -> book));
        return bookIds.stream()
                .map(booksById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Book filterBook(Book dto, Long userId) {
        if (dto.getShelves() != null && userId != null) {
            dto.setShelves(dto.getShelves().stream()
                    .filter(shelf -> userId.equals(shelf.getUserId()))
                    .collect(Collectors.toSet()));
        }
        return dto;
    }
}
