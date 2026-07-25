package org.booklore.repository.jooq

import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.OrderField
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository

@Repository
class JooqAppBookRepository(private val dsl: DSLContext) {

    private val bm = BOOK_METADATA

    private val sortFieldMap: Map<String, Field<*>> = mapOf(
        "addedOn" to BOOK.ADDED_ON,
        "scannedOn" to BOOK.SCANNED_ON,
        "metadata.title" to bm.TITLE,
        "metadata.seriesName" to bm.SERIES_NAME,
        "metadata.seriesNumber" to bm.SERIES_NUMBER,
    )

    fun findBookIds(condition: Condition, pageable: Pageable): Page<Long> {
        val count = dsl.selectCount()
            .from(BOOK)
            .where(condition)
            .fetchOne(0, Long::class.java) ?: 0L

        if (count == 0L) return PageableHelper.toPage(emptyList(), 0L, pageable)

        val ids = dsl.select(BOOK.ID)
            .from(BOOK)
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(condition)
            .orderBy(*toOrderFields(pageable.sort))
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch(BOOK.ID)

        return PageableHelper.toPage(ids, count, pageable)
    }

    fun findAllBookIds(condition: Condition): List<Long> =
        dsl.select(BOOK.ID)
            .from(BOOK)
            .where(condition)
            .fetch(BOOK.ID)

    fun countBooks(condition: Condition): Long =
        dsl.selectCount()
            .from(BOOK)
            .where(condition)
            .fetchOne(0, Long::class.java) ?: 0L

    private fun toOrderFields(sort: Sort): Array<OrderField<*>> {
        if (sort.isUnsorted) return arrayOf(BOOK.ADDED_ON.desc())
        return sort.mapNotNull { order ->
            sortFieldMap[order.property]?.let { field ->
                if (order.isAscending) field.asc() else field.desc()
            }
        }.toTypedArray()
    }
}
