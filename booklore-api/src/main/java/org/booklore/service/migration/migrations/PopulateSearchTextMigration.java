package org.booklore.service.migration.migrations;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqBookMetadataRelationsRepository;
import org.booklore.repository.jooq.JooqBookMetadataRepository;
import org.booklore.service.migration.Migration;
import org.booklore.util.BookUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopulateSearchTextMigration implements Migration {

    private final JooqBookEntityRepository jooqBookEntityRepository;
    private final JooqBookMetadataRelationsRepository relationsRepository;
    private final JooqBookMetadataRepository jooqBookMetadataRepository;

    @Override
    public String getKey() {
        return "populateSearchText";
    }

    @Override
    public String getDescription() {
        return "Populate search_text column for all books";
    }

    @Override
    public void execute() {
        log.info("Starting migration: {}", getKey());

        int batchSize = 1000;
        int processedCount = 0;
        int offset = 0;

        while (true) {
            List<BookEntity> bookBatch = jooqBookEntityRepository.findBooksForMigrationBatch(offset, batchSize);
            if (bookBatch.isEmpty()) break;

            List<Long> bookIds = bookBatch.stream().map(BookEntity::getId).toList();
            List<BookEntity> books = jooqBookEntityRepository.findAllByIdsIncludingDeleted(new LinkedHashSet<>(bookIds));
            Map<Long, List<String>> authorNamesByBookId = relationsRepository.findAuthorNamesByBookIds(bookIds);

            for (BookEntity book : books) {
                BookMetadataEntity m = book.getMetadata();
                if (m != null) {
                    try {
                        m.setAuthorNames(authorNamesByBookId.getOrDefault(book.getId(), Collections.emptyList()));
                        String searchText = BookUtils.buildSearchText(m);
                        m.setSearchText(searchText);
                        jooqBookMetadataRepository.updateSearchText(book.getId(), searchText);
                    } catch (Exception ex) {
                        log.warn("Failed to build search text for book {}: {}", book.getId(), ex.getMessage());
                    }
                }
            }

            processedCount += books.size();
            offset += batchSize;

            log.info("Migration progress: {} books processed", processedCount);

            if (bookBatch.size() < batchSize) break;
        }

        log.info("Completed migration '{}'. Total books processed: {}", getKey(), processedCount);
    }
}

