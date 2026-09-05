package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.Annotation;
import org.booklore.model.dto.CreateAnnotationRequest;
import org.booklore.model.dto.UpdateAnnotationRequest;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqAnnotationRepository;
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
public class AnnotationService {

    private final JooqAnnotationRepository annotationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public List<Annotation> getAnnotationsForBook(Long bookId) {
        Long userId = getCurrentUserId();
        return annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId);
    }

    @Transactional(readOnly = true)
    public Annotation getAnnotationById(Long annotationId) {
        return findAnnotationByIdAndUser(annotationId);
    }

    @Transactional
    public Annotation createAnnotation(CreateAnnotationRequest request) {
        Long userId = getCurrentUserId();
        validateNoDuplicateAnnotation(request.getCfi(), request.getBookId(), userId);

        if (!bookRepository.existsById(request.getBookId())) {
            throw new EntityNotFoundException("Book not found: " + request.getBookId());
        }
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }

        String color = request.getColor() != null ? request.getColor() : "#FFFF00";
        String style = request.getStyle() != null ? request.getStyle() : "highlight";

        log.info("Creating annotation for book {} by user {}", request.getBookId(), userId);
        return annotationRepository.insert(
                request.getBookId(),
                userId,
                request.getCfi(),
                request.getText(),
                color,
                style,
                request.getNote(),
                request.getChapterTitle());
    }

    @Transactional
    public Annotation updateAnnotation(Long annotationId, UpdateAnnotationRequest request) {
        Annotation annotation = findAnnotationByIdAndUser(annotationId);

        applyUpdates(annotation, request);

        log.info("Updating annotation {}", annotationId);
        return annotationRepository.update(annotation);
    }

    @Transactional
    public void deleteAnnotation(Long annotationId) {
        Annotation annotation = findAnnotationByIdAndUser(annotationId);
        log.info("Deleting annotation {}", annotationId);
        annotationRepository.deleteById(annotation.getId());
    }

    private Long getCurrentUserId() {
        return authenticationService.getAuthenticatedUser().getId();
    }

    private Annotation findAnnotationByIdAndUser(Long annotationId) {
        Long userId = getCurrentUserId();
        Annotation annotation = annotationRepository.findByIdAndUserId(annotationId, userId);
        if (annotation == null) {
            throw new EntityNotFoundException("Annotation not found: " + annotationId);
        }
        return annotation;
    }

    private void validateNoDuplicateAnnotation(String cfi, Long bookId, Long userId) {
        boolean exists = annotationRepository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId);
        if (exists) {
            throw new APIException("Annotation already exists at this location", HttpStatus.CONFLICT);
        }
    }

    private void applyUpdates(Annotation annotation, UpdateAnnotationRequest request) {
        Optional.ofNullable(request.getColor()).ifPresent(annotation::setColor);
        Optional.ofNullable(request.getStyle()).ifPresent(annotation::setStyle);
        Optional.ofNullable(request.getNote()).ifPresent(annotation::setNote);
    }
}
