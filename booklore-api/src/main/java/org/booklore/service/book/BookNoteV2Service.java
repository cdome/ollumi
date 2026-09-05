package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookNoteV2;
import org.booklore.model.dto.CreateBookNoteV2Request;
import org.booklore.model.dto.UpdateBookNoteV2Request;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqBookNoteV2Repository;
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
public class BookNoteV2Service {

    private final JooqBookNoteV2Repository bookNoteV2Repository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public List<BookNoteV2> getNotesForBook(Long bookId) {
        Long userId = getCurrentUserId();
        return bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId);
    }

    @Transactional(readOnly = true)
    public BookNoteV2 getNoteById(Long noteId) {
        return findNoteByIdAndUser(noteId);
    }

    @Transactional
    public BookNoteV2 createNote(CreateBookNoteV2Request request) {
        Long userId = getCurrentUserId();
        validateNoDuplicateNote(request.getCfi(), request.getBookId(), userId);

        if (!bookRepository.existsById(request.getBookId())) {
            throw new EntityNotFoundException("Book not found: " + request.getBookId());
        }
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }

        String color = request.getColor() != null ? request.getColor() : "#FFC107";

        log.info("Creating note for book {} by user {}", request.getBookId(), userId);
        return bookNoteV2Repository.insert(
                request.getBookId(),
                userId,
                request.getCfi(),
                request.getSelectedText(),
                request.getNoteContent(),
                color,
                request.getChapterTitle());
    }

    @Transactional
    public BookNoteV2 updateNote(Long noteId, UpdateBookNoteV2Request request) {
        BookNoteV2 note = findNoteByIdAndUser(noteId);

        applyUpdates(note, request);

        log.info("Updating note {}", noteId);
        return bookNoteV2Repository.update(note);
    }

    @Transactional
    public void deleteNote(Long noteId) {
        BookNoteV2 note = findNoteByIdAndUser(noteId);
        log.info("Deleting note {}", noteId);
        bookNoteV2Repository.deleteById(note.getId());
    }

    private Long getCurrentUserId() {
        return authenticationService.getAuthenticatedUser().getId();
    }

    private BookNoteV2 findNoteByIdAndUser(Long noteId) {
        Long userId = getCurrentUserId();
        BookNoteV2 note = bookNoteV2Repository.findByIdAndUserId(noteId, userId);
        if (note == null) {
            throw new EntityNotFoundException("Note not found: " + noteId);
        }
        return note;
    }

    private void validateNoDuplicateNote(String cfi, Long bookId, Long userId) {
        boolean exists = bookNoteV2Repository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId);
        if (exists) {
            throw new APIException("Note already exists at this location", HttpStatus.CONFLICT);
        }
    }

    private void applyUpdates(BookNoteV2 note, UpdateBookNoteV2Request request) {
        Optional.ofNullable(request.getNoteContent()).ifPresent(note::setNoteContent);
        Optional.ofNullable(request.getColor()).ifPresent(note::setColor);
        Optional.ofNullable(request.getChapterTitle()).ifPresent(note::setChapterTitle);
    }
}
