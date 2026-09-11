package org.booklore.service;

import lombok.AllArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookMetadataRelationsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class AuthorService {

    private final JooqBookMetadataRelationsRepository relationsRepository;
    private final JooqBookEntityRepository jooqBookEntityRepository;

    @Transactional(readOnly = true)
    public List<String> getAuthorsByBookId(Long bookId) {
        if (!jooqBookEntityRepository.existsById(bookId)) {
            throw ApiError.BOOK_NOT_FOUND.createException(bookId);
        }
        return relationsRepository.findAuthorNamesByBookId(bookId);
    }
}
