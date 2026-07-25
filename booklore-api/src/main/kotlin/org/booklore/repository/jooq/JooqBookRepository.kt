package org.booklore.repository.jooq

import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.model.enums.BookFileType
import org.booklore.repository.jooq.dto.BookCoverUpdate
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

@Repository
class JooqBookRepository(private val dsl: DSLContext) {

    private val bm = BOOK_METADATA
    private val bf = BOOK_FILE

    // ========================================================================
    // Book IDs
    // ========================================================================

    fun findBookIdsByLibraryId(libraryId: Long): Set<Long> =
        dsl.select(BOOK.ID)
            .from(BOOK)
            .where(BookConditions.notDeleted())
            .and(BOOK.LIBRARY_ID.eq(libraryId))
            .fetchSet(BOOK.ID)

    fun findBookIdsByLibraryPathIds(libraryPathIds: Collection<Long>): List<Long> =
        dsl.select(BOOK.ID)
            .from(BOOK)
            .where(BookConditions.notDeleted())
            .and(BOOK.LIBRARY_PATH_ID.`in`(libraryPathIds))
            .fetch(BOOK.ID)

    // ========================================================================
    // Counts
    // ========================================================================

    fun countByIds(bookIds: Collection<Long>): Long =
        dsl.fetchCount(BOOK, BookConditions.notDeleted().and(BOOK.ID.`in`(bookIds))).toLong()

    fun countByLibraryId(libraryId: Long): Long =
        dsl.fetchCount(BOOK, BookConditions.notDeleted().and(BOOK.LIBRARY_ID.eq(libraryId))).toLong()

    fun countByBookType(type: BookFileType): Long =
        countByBookType(type, null)

    fun countByLibraryIdAndBookType(libraryId: Long, type: BookFileType): Long =
        countByBookType(type, libraryId)

    fun countSoftDeleted(): Long =
        dsl.fetchCount(BOOK, BOOK.DELETED.eq(1)).toLong()

    private fun countByBookType(type: BookFileType, libraryId: Long?): Long =
        dsl.select(countDistinct(BOOK.ID))
            .from(BOOK)
            .join(bf).on(bf.BOOK_ID.eq(BOOK.ID))
            .where(BookConditions.notDeleted())
            .and(bf.IS_BOOK.eq(1))
            .and(bf.BOOK_TYPE.eq(type.name))
            .and(if (libraryId != null) BOOK.LIBRARY_ID.eq(libraryId) else noCondition())
            .fetchOne(0, Long::class.java)!!

    // ========================================================================
    // Cover update info
    // ========================================================================

    fun findCoverUpdateInfoByIds(bookIds: Collection<Long>): List<BookCoverUpdate> =
        dsl.select(BOOK.ID, bm.COVER_UPDATED_ON)
            .from(BOOK)
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(BOOK.ID.`in`(bookIds))
            .fetch { record ->
                BookCoverUpdate(
                    id = record[BOOK.ID]!!,
                    coverUpdatedOn = record[bm.COVER_UPDATED_ON]?.toInstant(ZoneOffset.UTC)
                )
            }

    // ========================================================================
    // Distinct series names (Komga API)
    // ========================================================================

    /**
     * Distinct series names; books without a series name are grouped under [unknownSeriesName].
     */
    fun findDistinctSeriesNamesGrouped(unknownSeriesName: String, libraryId: Long?): List<String> {
        val seriesName = coalesce(bm.SERIES_NAME, inline(unknownSeriesName))
        return dsl.selectDistinct(seriesName)
            .from(BOOK)
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(BookConditions.notDeleted())
            .and(if (libraryId != null) BOOK.LIBRARY_ID.eq(libraryId) else noCondition())
            .orderBy(seriesName)
            .fetch(seriesName)
    }

    /**
     * Distinct series names; each book without a series name gets its own entry
     * (metadata title, or the first book file's name as last resort).
     */
    fun findDistinctSeriesNamesUngrouped(libraryId: Long?): List<String> {
        val bf2 = BOOK_FILE.`as`("bf2")
        val firstBookFileName = field(
            select(bf2.FILE_NAME)
                .from(bf2)
                .where(bf2.BOOK_ID.eq(BOOK.ID))
                .and(bf2.IS_BOOK.eq(1))
                .orderBy(bf2.ID.asc())
                .limit(1)
        )
        val seriesName = coalesce(bm.SERIES_NAME, bm.TITLE, firstBookFileName)
        return dsl.selectDistinct(seriesName)
            .from(BOOK)
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(BookConditions.notDeleted())
            .and(if (libraryId != null) BOOK.LIBRARY_ID.eq(libraryId) else noCondition())
            .orderBy(seriesName)
            .fetch(seriesName)
    }
}
