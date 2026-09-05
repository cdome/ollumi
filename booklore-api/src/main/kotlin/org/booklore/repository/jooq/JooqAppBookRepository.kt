package org.booklore.repository.jooq

import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.repository.jooq.dto.AuthorFacet
import org.booklore.repository.jooq.dto.LanguageFacet
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.OrderField
import org.jooq.impl.DSL.countDistinct
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository

@Repository
class JooqAppBookRepository(private val dsl: DSLContext) {

    private val bm = BOOK_METADATA
    private val bf = BOOK_FILE
    private val bam = BOOK_METADATA_AUTHOR_MAPPING

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

    // ========================================================================
    // Filter-option facets (scope [condition] built from AppBookConditions)
    // ========================================================================

    fun findAuthorFacets(condition: Condition, limit: Int): List<AuthorFacet> {
        val bookCount = countDistinct(BOOK.ID)
        return dsl.select(AUTHOR.NAME, bookCount)
            .from(BOOK)
            .join(bam).on(bam.BOOK_ID.eq(BOOK.ID))
            .join(AUTHOR).on(AUTHOR.ID.eq(bam.AUTHOR_ID))
            .where(condition)
            .groupBy(AUTHOR.NAME)
            .orderBy(bookCount.desc())
            .limit(limit)
            .fetch { AuthorFacet(it[AUTHOR.NAME]!!, it[bookCount].toLong()) }
    }

    fun findLanguageFacets(condition: Condition): List<LanguageFacet> {
        val bookCount = countDistinct(BOOK.ID)
        return dsl.select(bm.LANGUAGE, bookCount)
            .from(BOOK)
            .join(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(condition)
            .and(bm.LANGUAGE.isNotNull)
            .and(bm.LANGUAGE.ne(""))
            .groupBy(bm.LANGUAGE)
            .orderBy(bookCount.desc())
            .fetch { LanguageFacet(it[bm.LANGUAGE]!!, it[bookCount].toLong()) }
    }

    fun findFileTypes(condition: Condition): List<String> =
        dsl.selectDistinct(bf.BOOK_TYPE)
            .from(BOOK)
            .join(bf).on(bf.BOOK_ID.eq(BOOK.ID))
            .where(condition)
            .and(bf.IS_BOOK.eq(1))
            .fetch(bf.BOOK_TYPE)

    private fun toOrderFields(sort: Sort): Array<OrderField<*>> {
        if (sort.isUnsorted) return arrayOf(BOOK.ADDED_ON.desc())
        return sort.mapNotNull { order ->
            sortFieldMap[order.property]?.let { field ->
                if (order.isAscending) field.asc() else field.desc()
            }
        }.toTypedArray()
    }
}
