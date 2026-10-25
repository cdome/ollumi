package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqUserRepository;
import org.booklore.repository.jooq.JooqPdfAnnotationRepository;
import org.booklore.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfAnnotationService {

    private final JooqPdfAnnotationRepository pdfAnnotationRepository;
    private final JooqBookEntityRepository jooqBookEntityRepository;
    private final JooqUserRepository userRepository;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public Optional<String> getAnnotations(Long bookId) {
        Long userId = getCurrentUserId();
        return Optional.ofNullable(pdfAnnotationRepository.findDataByBookIdAndUserId(bookId, userId));
    }

    @Transactional
    public void saveAnnotations(Long bookId, String data) {
        Long userId = getCurrentUserId();
        if (!jooqBookEntityRepository.existsById(bookId)) {
            throw new EntityNotFoundException("Book not found: " + bookId);
        }
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
        pdfAnnotationRepository.upsert(bookId, userId, data);
        log.info("Saved PDF annotations for book {} by user {}", bookId, userId);
    }

    @Transactional
    public void deleteAnnotations(Long bookId) {
        Long userId = getCurrentUserId();
        pdfAnnotationRepository.deleteByBookIdAndUserId(bookId, userId);
        log.info("Deleted PDF annotations for book {} by user {}", bookId, userId);
    }

    private Long getCurrentUserId() {
        return authenticationService.getAuthenticatedUser().getId();
    }
}
