package org.booklore.repository.jooq

import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookShelfMapping.BOOK_SHELF_MAPPING
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class JooqBookOpdsRepository(private val dsl: DSLContext) {

    private val bm = BOOK_METADATA
    private val bsm = BOOK_SHELF_MAPPING
    private val bam = BOOK_METADATA_AUTHOR_MAPPING

    // ========================================================================
    // All books / recent books (same query, different page sizes)
    // ========================================================================

    fun findBookIds(pageable: Pageable): Page<Long> =
        paginatedBookIds(BookConditions.notDeleted(), pageable)

    // ========================================================================
    // Books by library IDs
    // ========================================================================

    fun findBookIdsByLibraryIds(libraryIds: Collection<Long>, pageable: Pageable): Page<Long> =
        paginatedBookIds(BookConditions.notDeleted().and(BOOK.LIBRARY_ID.`in`(libraryIds)), pageable)

    // ========================================================================
    // Books by shelf ID / shelf IDs
    // ========================================================================

    fun findBookIdsByShelfId(shelfId: Long, pageable: Pageable): Page<Long> {
        val condition = BookConditions.notDeleted().and(bsm.SHELF_ID.eq(shelfId))
        return paginatedDistinctBookIds(
            { it.join(bsm).on(bsm.BOOK_ID.eq(BOOK.ID)) },
            condition, pageable
        )
    }

    fun findBookIdsByShelfIds(shelfIds: Collection<Long>, pageable: Pageable): Page<Long> {
        val condition = BookConditions.notDeleted().and(bsm.SHELF_ID.`in`(shelfIds))
        return paginatedDistinctBookIds(
            { it.join(bsm).on(bsm.BOOK_ID.eq(BOOK.ID)) },
            condition, pageable
        )
    }

    // ========================================================================
    // Search by metadata
    // ========================================================================

    fun findBookIdsByMetadataSearch(text: String, pageable: Pageable): Page<Long> {
        val condition = BookConditions.notDeleted().and(bm.SEARCH_TEXT.like("%$text%"))
        return paginatedDistinctBookIds(
            { it.leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID)) },
            condition, pageable
        )
    }

    fun findBookIdsByMetadataSearchAndLibraryIds(
        text: String, libraryIds: Collection<Long>, pageable: Pageable
    ): Page<Long> {
        val condition = BookConditions.notDeleted()
            .and(BOOK.LIBRARY_ID.`in`(libraryIds))
            .and(bm.SEARCH_TEXT.like("%$text%"))
        return paginatedDistinctBookIds(
            { it.leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID)) },
            condition, pageable
        )
    }

    fun findBookIdsByMetadataSearchAndShelfIds(
        text: String, shelfIds: Collection<Long>, pageable: Pageable
    ): Page<Long> {
        val condition = BookConditions.notDeleted()
            .and(bsm.SHELF_ID.`in`(shelfIds))
            .and(bm.SEARCH_TEXT.like("%$text%"))
        return paginatedDistinctBookIds(
            { it.leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID)).join(bsm).on(bsm.BOOK_ID.eq(BOOK.ID)) },
            condition, pageable
        )
    }

    // ========================================================================
    // Books by author
    // ========================================================================

    fun findBookIdsByAuthorName(authorName: String, pageable: Pageable): Page<Long> {
        val condition = BookConditions.notDeleted().and(AUTHOR.NAME.eq(authorName))
        return paginatedDistinctBookIds(
            { it.join(bm).on(bm.BOOK_ID.eq(BOOK.ID)).join(bam).on(bam.BOOK_ID.eq(bm.BOOK_ID)).join(AUTHOR).on(AUTHOR.ID.eq(bam.AUTHOR_ID)) },
            condition, pageable
        )
    }

    fun findBookIdsByAuthorNameAndLibraryIds(
        authorName: String, libraryIds: Collection<Long>, pageable: Pageable
    ): Page<Long> {
        val condition = BookConditions.notDeleted()
            .and(AUTHOR.NAME.eq(authorName))
            .and(BOOK.LIBRARY_ID.`in`(libraryIds))
        return paginatedDistinctBookIds(
            { it.join(bm).on(bm.BOOK_ID.eq(BOOK.ID)).join(bam).on(bam.BOOK_ID.eq(bm.BOOK_ID)).join(AUTHOR).on(AUTHOR.ID.eq(bam.AUTHOR_ID)) },
            condition, pageable
        )
    }

    // ========================================================================
    // Books by series
    // ========================================================================

    fun findBookIdsBySeriesName(seriesName: String, pageable: Pageable): Page<Long> {
        val condition = BookConditions.notDeleted().and(bm.SERIES_NAME.eq(seriesName))

        val total = dsl.select(countDistinct(BOOK.ID))
            .from(BOOK).join(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(condition)
            .fetchOne(0, Long::class.java)!!

        val ids = dsl.selectDistinct(BOOK.ID)
            .from(BOOK).join(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(condition)
            .orderBy(coalesce(bm.SERIES_NUMBER, inline(999999.0)).asc(), BOOK.ADDED_ON.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch(BOOK.ID)

        return PageableHelper.toPage(ids, total, pageable)
    }

    fun findBookIdsBySeriesNameAndLibraryIds(
        seriesName: String, libraryIds: Collection<Long>, pageable: Pageable
    ): Page<Long> {
        val condition = BookConditions.notDeleted()
            .and(bm.SERIES_NAME.eq(seriesName))
            .and(BOOK.LIBRARY_ID.`in`(libraryIds))

        val total = dsl.select(countDistinct(BOOK.ID))
            .from(BOOK).join(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(condition)
            .fetchOne(0, Long::class.java)!!

        val ids = dsl.selectDistinct(BOOK.ID)
            .from(BOOK).join(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(condition)
            .orderBy(coalesce(bm.SERIES_NUMBER, inline(999999.0)).asc(), BOOK.ADDED_ON.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch(BOOK.ID)

        return PageableHelper.toPage(ids, total, pageable)
    }

    // ========================================================================
    // Random books
    // ========================================================================

    fun findRandomBookIds(): List<Long> =
        dsl.select(BOOK.ID)
            .from(BOOK)
            .where(BookConditions.notDeleted())
            .orderBy(rand())
            .fetch(BOOK.ID)

    fun findRandomBookIdsByLibraryIds(libraryIds: Collection<Long>): List<Long> =
        dsl.select(BOOK.ID)
            .from(BOOK)
            .where(BookConditions.notDeleted())
            .and(BOOK.LIBRARY_ID.`in`(libraryIds))
            .orderBy(rand())
            .fetch(BOOK.ID)

    // ========================================================================
    // Distinct authors
    // ========================================================================

    fun findDistinctAuthorNames(): List<String> =
        dsl.selectDistinct(AUTHOR.NAME)
            .from(AUTHOR)
            .join(bam).on(bam.AUTHOR_ID.eq(AUTHOR.ID))
            .join(bm).on(bm.BOOK_ID.eq(bam.BOOK_ID))
            .join(BOOK).on(BOOK.ID.eq(bm.BOOK_ID))
            .where(BookConditions.notDeleted())
            .and(AUTHOR.NAME.isNotNull)
            .orderBy(AUTHOR.NAME)
            .fetch(AUTHOR.NAME)

    fun findDistinctAuthorNamesByLibraryIds(libraryIds: Collection<Long>): List<String> =
        dsl.selectDistinct(AUTHOR.NAME)
            .from(AUTHOR)
            .join(bam).on(bam.AUTHOR_ID.eq(AUTHOR.ID))
            .join(bm).on(bm.BOOK_ID.eq(bam.BOOK_ID))
            .join(BOOK).on(BOOK.ID.eq(bm.BOOK_ID))
            .where(BookConditions.notDeleted())
            .and(BOOK.LIBRARY_ID.`in`(libraryIds))
            .and(AUTHOR.NAME.isNotNull)
            .orderBy(AUTHOR.NAME)
            .fetch(AUTHOR.NAME)

    // ========================================================================
    // Distinct series
    // ========================================================================

    fun findDistinctSeries(): List<String> =
        dsl.selectDistinct(bm.SERIES_NAME)
            .from(bm)
            .join(BOOK).on(BOOK.ID.eq(bm.BOOK_ID))
            .where(BookConditions.notDeleted())
            .and(bm.SERIES_NAME.isNotNull)
            .and(bm.SERIES_NAME.ne(""))
            .orderBy(bm.SERIES_NAME)
            .fetch(bm.SERIES_NAME)

    fun findDistinctSeriesByLibraryIds(libraryIds: Collection<Long>): List<String> =
        dsl.selectDistinct(bm.SERIES_NAME)
            .from(bm)
            .join(BOOK).on(BOOK.ID.eq(bm.BOOK_ID))
            .where(BookConditions.notDeleted())
            .and(BOOK.LIBRARY_ID.`in`(libraryIds))
            .and(bm.SERIES_NAME.isNotNull)
            .and(bm.SERIES_NAME.ne(""))
            .orderBy(bm.SERIES_NAME)
            .fetch(bm.SERIES_NAME)

    // ========================================================================
    // Shared helpers
    // ========================================================================

    private fun paginatedBookIds(condition: Condition, pageable: Pageable): Page<Long> {
        val total = dsl.fetchCount(BOOK, condition)

        val ids = dsl.select(BOOK.ID)
            .from(BOOK)
            .where(condition)
            .orderBy(BOOK.ADDED_ON.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch(BOOK.ID)

        return PageableHelper.toPage(ids, total.toLong(), pageable)
    }

    private fun paginatedDistinctBookIds(
        joinBuilder: (org.jooq.SelectJoinStep<*>) -> org.jooq.SelectOnConditionStep<*>,
        condition: Condition,
        pageable: Pageable
    ): Page<Long> {
        val countQuery = dsl.select(countDistinct(BOOK.ID)).from(BOOK)
        joinBuilder(countQuery)
        val total = countQuery.where(condition).fetchOne(0, Long::class.java)!!

        val idsQuery = dsl.selectDistinct(BOOK.ID, BOOK.ADDED_ON).from(BOOK)
        joinBuilder(idsQuery)
        val ids = idsQuery
            .where(condition)
            .orderBy(BOOK.ADDED_ON.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch(BOOK.ID)

        return PageableHelper.toPage(ids, total, pageable)
    }
}
