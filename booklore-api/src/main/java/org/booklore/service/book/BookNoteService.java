package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookNote;
import org.booklore.model.dto.CreateBookNoteRequest;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqBookNoteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class BookNoteService {

    private final JooqBookNoteRepository bookNoteRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public List<BookNote> getNotesForBook(Long bookId) {
        BookLoreUser currentUser = authenticationService.getAuthenticatedUser();
        return bookNoteRepository.findByBookIdAndUserIdOrderByUpdatedAtDesc(bookId, currentUser.getId());
    }

    @Transactional
    public BookNote createOrUpdateNote(CreateBookNoteRequest request) {
        BookLoreUser currentUser = authenticationService.getAuthenticatedUser();

        if (!bookRepository.existsById(request.getBookId())) {
            throw ApiError.BOOK_NOT_FOUND.createException(request.getBookId());
        }
        if (!userRepository.existsById(currentUser.getId())) {
            throw new EntityNotFoundException("User not found: " + currentUser.getId());
        }

        if (request.getId() != null) {
            if (bookNoteRepository.findByIdAndUserId(request.getId(), currentUser.getId()) == null) {
                throw new EntityNotFoundException("Note not found: " + request.getId());
            }
            return bookNoteRepository.update(request.getId(), currentUser.getId(), request.getTitle(), request.getContent());
        }

        return bookNoteRepository.insert(request.getBookId(), currentUser.getId(), request.getTitle(), request.getContent());
    }

    @Transactional
    public void deleteNote(Long noteId) {
        BookLoreUser currentUser = authenticationService.getAuthenticatedUser();
        BookNote note = bookNoteRepository.findByIdAndUserId(noteId, currentUser.getId());
        if (note == null) {
            throw new EntityNotFoundException("Note not found: " + noteId);
        }
        bookNoteRepository.deleteById(noteId);
    }
}
