package org.booklore.service.book;

import org.booklore.config.BookmarkProperties;
import org.booklore.model.dto.BookMark;
import org.booklore.model.dto.CreateBookMarkRequest;
import org.booklore.model.dto.UpdateBookMarkRequest;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqBookMarkRepository;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookMarkService {

    private final JooqBookMarkRepository bookMarkRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final BookmarkProperties bookmarkProperties;

    @Transactional(readOnly = true)
    public List<BookMark> getBookmarksForBook(Long bookId) {
        Long userId = getCurrentUserId();
        return bookMarkRepository.findByBookIdAndUserIdOrderByPriorityAscCreatedAtDesc(bookId, userId);
    }

    @Transactional(readOnly = true)
    public BookMark getBookmarkById(Long bookmarkId) {
        return findBookmarkByIdAndUser(bookmarkId);
    }

    @Transactional
    public BookMark createBookmark(CreateBookMarkRequest request) {
        Long userId = getCurrentUserId();

        // Validate no duplicate based on bookmark type
        if (request.isAudiobookBookmark()) {
            validateNoDuplicateAudiobookBookmark(request.getPositionMs(), request.getTrackIndex(), request.getBookId(), userId);
        } else if (request.getCfi() != null) {
            validateNoDuplicateBookmark(request.getCfi(), request.getBookId(), userId);
        }

        if (!bookRepository.existsById(request.getBookId())) {
            throw new EntityNotFoundException("Book not found: " + request.getBookId());
        }
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }

        log.info("Creating bookmark for book {} by user {}", request.getBookId(), userId);
        return bookMarkRepository.insert(
                request.getBookId(),
                userId,
                request.getCfi(),
                request.getPositionMs(),
                request.getTrackIndex(),
                request.getTitle(),
                bookmarkProperties.getDefaultPriority());
    }

    @Transactional
    public BookMark updateBookmark(Long bookmarkId, UpdateBookMarkRequest request) {
        BookMark bookmark = findBookmarkByIdAndUser(bookmarkId);

        // Validate CFI uniqueness if CFI is being updated
        if (request.getCfi() != null) {
            validateNoDuplicateBookmark(request.getCfi(), bookmark.getBookId(), bookmark.getUserId(), bookmarkId);
        }

        applyUpdates(bookmark, request);

        log.info("Updating bookmark {}", bookmarkId);
        return bookMarkRepository.update(bookmark);
    }

    @Transactional
    public void deleteBookmark(Long bookmarkId) {
        BookMark bookmark = findBookmarkByIdAndUser(bookmarkId);
        log.info("Deleting bookmark {}", bookmarkId);
        bookMarkRepository.deleteById(bookmark.getId());
    }

    private Long getCurrentUserId() {
        return authenticationService.getAuthenticatedUser().getId();
    }

    private BookMark findBookmarkByIdAndUser(Long bookmarkId) {
        Long userId = getCurrentUserId();
        BookMark bookmark = bookMarkRepository.findByIdAndUserId(bookmarkId, userId);
        if (bookmark == null) {
            throw new EntityNotFoundException("Bookmark not found: " + bookmarkId);
        }
        return bookmark;
    }

    private void validateNoDuplicateBookmark(String cfi, Long bookId, Long userId) {
        validateNoDuplicateBookmark(cfi, bookId, userId, null);
    }

    /**
     * Priority: 1 (highest/most important) to 5 (lowest/least important).
     * Bookmarks are sorted by priority ascending (1 first), then by creation date descending.
     */
    private void validateNoDuplicateBookmark(String cfi, Long bookId, Long userId, Long excludeBookmarkId) {
        boolean exists = bookMarkRepository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId, excludeBookmarkId);
        if (exists) {
            throw new APIException("Bookmark already exists at this location", HttpStatus.CONFLICT);
        }
    }

    /**
     * Validate no duplicate audiobook bookmark exists within 5 seconds of the position.
     */
    private void validateNoDuplicateAudiobookBookmark(Long positionMs, Integer trackIndex, Long bookId, Long userId) {
        boolean exists = bookMarkRepository.existsByPositionMsNearAndBookIdAndUserId(positionMs, trackIndex, bookId, userId);
        if (exists) {
            throw new APIException("A bookmark already exists near this position", HttpStatus.CONFLICT);
        }
    }

    private void applyUpdates(BookMark bookmark, UpdateBookMarkRequest request) {
        Optional.ofNullable(request.getTitle()).ifPresent(bookmark::setTitle);
        Optional.ofNullable(request.getCfi()).ifPresent(bookmark::setCfi);
        Optional.ofNullable(request.getColor()).ifPresent(bookmark::setColor);
        Optional.ofNullable(request.getNotes()).ifPresent(bookmark::setNotes);
        Optional.ofNullable(request.getPriority()).ifPresent(bookmark::setPriority);
    }
}
