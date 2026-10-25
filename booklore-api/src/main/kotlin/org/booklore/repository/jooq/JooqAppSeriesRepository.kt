package org.booklore.repository.jooq

import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.booklore.repository.jooq.dto.SeriesAggregate
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL.count
import org.jooq.impl.DSL.countDistinct
import org.jooq.impl.DSL.exists
import org.jooq.impl.DSL.`when`
import org.jooq.impl.DSL.inline
import org.jooq.impl.DSL.lower
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.noCondition
import org.jooq.impl.DSL.selectOne
import org.jooq.impl.DSL.sum
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class JooqAppSeriesRepository(private val dsl: DSLContext) {

    private val bm = BOOK_METADATA
    private val ubp = USER_BOOK_PROGRESS
    private val bf = BOOK_FILE

    fun findSeriesAggregates(
        userId: Long,
        accessibleLibraryIds: Set<Long>?,
        libraryId: Long?,
        search: String?,
        inProgressOnly: Boolean,
        sortBy: String?,
        sortDir: String?,
        offset: Int,
        limit: Int
    ): List<SeriesAggregate> {
        val bookCount = count(BOOK.ID)
        val maxTotal = max(bm.SERIES_TOTAL)
        val maxAdded = max(BOOK.ADDED_ON)
        val maxLastRead = max(ubp.LAST_READ_TIME)
        val booksRead = sum(`when`(ubp.READ_STATUS.eq("READ"), inline(1)).otherwise(inline(0)))
        val readingCount = sum(
            `when`(ubp.READ_STATUS.`in`("READING", "RE_READING"), inline(1)).otherwise(inline(0))
        )

        return dsl.select(bm.SERIES_NAME, bookCount, maxTotal, maxAdded, booksRead)
            .from(BOOK)
            .join(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .leftJoin(ubp).on(ubp.BOOK_ID.eq(BOOK.ID).and(ubp.USER_ID.eq(userId)))
            .where(baseFilter(accessibleLibraryIds, libraryId, search))
            .groupBy(bm.SERIES_NAME)
            .having(if (inProgressOnly) readingCount.gt(BigDecimal.ZERO) else noCondition())
            .orderBy(seriesOrder(sortBy, sortDir, inProgressOnly, bookCount, booksRead, maxAdded, maxLastRead))
            .limit(limit)
            .offset(offset)
            .fetch {
                SeriesAggregate(
                    seriesName = it[bm.SERIES_NAME]!!,
                    bookCount = it[bookCount].toLong(),
                    seriesTotal = it[maxTotal],
                    latestAddedOn = it[maxAdded],
                    booksRead = it[booksRead]?.toLong() ?: 0L
                )
            }
    }

    fun countSeries(
        userId: Long,
        accessibleLibraryIds: Set<Long>?,
        libraryId: Long?,
        search: String?,
        inProgressOnly: Boolean
    ): Long {
        if (!inProgressOnly) {
            return dsl.select(countDistinct(bm.SERIES_NAME))
                .from(BOOK)
                .join(bm).on(bm.BOOK_ID.eq(BOOK.ID))
                .where(baseFilter(accessibleLibraryIds, libraryId, search))
                .fetchOne(0, Long::class.java) ?: 0L
        }

        val readingCount = sum(
            `when`(ubp.READ_STATUS.`in`("READING", "RE_READING"), inline(1)).otherwise(inline(0))
        )
        val grouped = dsl.select(bm.SERIES_NAME)
            .from(BOOK)
            .join(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .leftJoin(ubp).on(ubp.BOOK_ID.eq(BOOK.ID).and(ubp.USER_ID.eq(userId)))
            .where(baseFilter(accessibleLibraryIds, libraryId, search))
            .groupBy(bm.SERIES_NAME)
            .having(readingCount.gt(BigDecimal.ZERO))
        return dsl.fetchCount(grouped).toLong()
    }

    /** Book IDs belonging to any of [seriesNames] within the accessible scope. */
    fun findBookIdsBySeriesNames(
        seriesNames: Collection<String>,
        accessibleLibraryIds: Set<Long>?,
        libraryId: Long?
    ): List<Long> {
        if (seriesNames.isEmpty()) return emptyList()
        return dsl.select(BOOK.ID)
            .from(BOOK)
            .join(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(BookConditions.notDeleted())
            .and(hasDigitalFile())
            .and(bm.SERIES_NAME.`in`(seriesNames))
            .and(libraryCondition(accessibleLibraryIds, libraryId))
            .fetch(BOOK.ID)
    }

    private fun baseFilter(accessibleLibraryIds: Set<Long>?, libraryId: Long?, search: String?): Condition {
        var condition = BookConditions.notDeleted()
            .and(hasDigitalFile())
            .and(bm.SERIES_NAME.isNotNull)
            .and(libraryCondition(accessibleLibraryIds, libraryId))
        if (!search.isNullOrBlank()) {
            condition = condition.and(lower(bm.SERIES_NAME).like("%${search.trim().lowercase()}%"))
        }
        return condition
    }

    private fun libraryCondition(accessibleLibraryIds: Set<Long>?, libraryId: Long?): Condition = when {
        libraryId != null -> BOOK.LIBRARY_ID.eq(libraryId)
        // Preserve JPA semantics: an empty accessible set matches no books.
        accessibleLibraryIds != null -> BOOK.LIBRARY_ID.`in`(accessibleLibraryIds)
        else -> noCondition()
    }

    private fun hasDigitalFile(): Condition =
        exists(selectOne().from(bf).where(bf.BOOK_ID.eq(BOOK.ID)))

    private fun seriesOrder(
        sortBy: String?,
        sortDir: String?,
        inProgressOnly: Boolean,
        bookCount: org.jooq.Field<Int>,
        booksRead: org.jooq.Field<BigDecimal>,
        maxAdded: org.jooq.Field<java.time.LocalDateTime>,
        maxLastRead: org.jooq.Field<java.time.LocalDateTime>
    ): SortField<*> {
        val asc = "asc".equals(sortDir, ignoreCase = true)
        return when (sortBy?.lowercase()) {
            "name" -> if (asc) bm.SERIES_NAME.asc() else bm.SERIES_NAME.desc()
            "bookcount" -> if (asc) bookCount.asc() else bookCount.desc()
            "readprogress" -> if (asc) booksRead.asc() else booksRead.desc()
            else -> {
                val dateField = if (inProgressOnly) maxLastRead else maxAdded
                if (asc) dateField.asc().nullsLast() else dateField.desc().nullsFirst()
            }
        }
    }
}
