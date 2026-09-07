package org.booklore.repository.jooq

import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/**
 * jOOQ write access to book_metadata's own scalar columns (not the @ManyToMany relations, see
 * JooqBookMetadataRelationsRepository). Used by bulk consolidate/delete operations that previously
 * relied on BookMetadataRepository.saveAll() + the @PreUpdate updateSearchText() hook.
 */
@Repository
class JooqBookMetadataRepository(private val dsl: DSLContext) {

    private val bm = BOOK_METADATA

    fun updateSeriesName(bookId: Long, seriesName: String?, searchText: String?) {
        dsl.update(bm)
            .set(bm.SERIES_NAME, seriesName)
            .set(bm.SEARCH_TEXT, searchText)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }

    fun clearSeries(bookId: Long, searchText: String?) {
        dsl.update(bm)
            .setNull(bm.SERIES_NAME)
            .setNull(bm.SERIES_NUMBER)
            .setNull(bm.SERIES_TOTAL)
            .set(bm.SEARCH_TEXT, searchText)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }

    fun updatePublisher(bookId: Long, publisher: String?) {
        dsl.update(bm)
            .set(bm.PUBLISHER, publisher)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }

    fun updateLanguage(bookId: Long, language: String?) {
        dsl.update(bm)
            .set(bm.LANGUAGE, language)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }

    fun updateSearchText(bookId: Long, searchText: String?) {
        dsl.update(bm)
            .set(bm.SEARCH_TEXT, searchText)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }
}
