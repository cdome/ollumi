package org.booklore.service.metadata;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookReview;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.jooq.JooqBookReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookReviewUpdateService {

    private final JooqBookReviewRepository bookReviewRepository;

    public void addReviewsToBook(List<BookReview> bookReviews, BookMetadataEntity e) {
        BookMetadata tempMetadata = BookMetadata.builder()
                .bookReviews(bookReviews)
                .build();
        MetadataClearFlags clearFlags = new MetadataClearFlags();
        updateBookReviews(tempMetadata, e, clearFlags, true);
    }

    public void updateBookReviews(BookMetadata metadata, BookMetadataEntity entity, MetadataClearFlags clearFlags, boolean mergeWithExisting) {
        if (Boolean.TRUE.equals(entity.getReviewsLocked())) {
            return;
        }
        Long bookId = entity.getBookId();
        if (clearFlags.isReviews()) {
            bookReviewRepository.deleteByBookId(bookId);
            return;
        }
        if (!isFieldUpdateAllowed(false, metadata.getBookReviews()) || metadata.getBookReviews() == null) {
            return;
        }

        List<BookReview> incoming = metadata.getBookReviews().stream()
                .filter(review -> review != null && review.getMetadataProvider() != null)
                .toList();

        List<BookReview> combined;
        if (mergeWithExisting) {
            combined = new ArrayList<>(bookReviewRepository.findByBookId(bookId));
            combined.addAll(incoming);
        } else {
            combined = incoming;
        }

        List<BookReview> limited = applyReviewLimitsPerProvider(combined);

        bookReviewRepository.deleteByBookId(bookId);
        for (BookReview review : limited) {
            bookReviewRepository.insert(bookId, review);
        }
    }

    private List<BookReview> applyReviewLimitsPerProvider(List<BookReview> reviews) {
        return reviews.stream()
                .collect(Collectors.groupingBy(BookReview::getMetadataProvider))
                .values()
                .stream()
                .flatMap(providerReviews -> providerReviews.stream()
                        .sorted(Comparator.comparing(BookReview::getDate,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(5))
                .collect(Collectors.toList());
    }

    private boolean isFieldUpdateAllowed(Boolean isLocked, Object fieldValue) {
        return (isLocked == null || !isLocked) && fieldValue != null;
    }
}
