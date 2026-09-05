package org.booklore.repository.jooq

import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.repository.jooq.dto.AuthorSummaryRow
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL.countDistinct
import org.jooq.impl.DSL.lower
import org.jooq.impl.DSL.noCondition
import org.springframework.stereotype.Repository

@Repository
class JooqAppAuthorRepository(private val dsl: DSLContext) {

    private val bam = BOOK_METADATA_AUTHOR_MAPPING
    private val bf = BOOK_FILE

    fun countAuthors(accessibleLibraryIds: Set<Long>?, libraryId: Long?, search: String?): Long =
        dsl.select(countDistinct(AUTHOR.ID))
            .from(AUTHOR)
            .join(bam).on(bam.AUTHOR_ID.eq(AUTHOR.ID))
            .join(BOOK).on(BOOK.ID.eq(bam.BOOK_ID))
            .where(baseFilter(accessibleLibraryIds, libraryId, search))
            .fetchOne(0, Long::class.java) ?: 0L

    fun findAuthorSummaries(
        accessibleLibraryIds: Set<Long>?,
        libraryId: Long?,
        search: String?,
        sortBy: String?,
        sortDir: String?,
        offset: Int,
        limit: Int
    ): List<AuthorSummaryRow> {
        val bookCount = countDistinct(BOOK.ID)
        return dsl.select(AUTHOR.ID, AUTHOR.NAME, AUTHOR.ASIN, bookCount)
            .from(AUTHOR)
            .join(bam).on(bam.AUTHOR_ID.eq(AUTHOR.ID))
            .join(BOOK).on(BOOK.ID.eq(bam.BOOK_ID))
            .where(baseFilter(accessibleLibraryIds, libraryId, search))
            .groupBy(AUTHOR.ID, AUTHOR.NAME, AUTHOR.ASIN)
            .orderBy(authorSort(sortBy, sortDir))
            .limit(limit)
            .offset(offset)
            .fetch {
                AuthorSummaryRow(
                    id = it[AUTHOR.ID]!!,
                    name = it[AUTHOR.NAME]!!,
                    asin = it[AUTHOR.ASIN],
                    bookCount = it[bookCount].toLong()
                )
            }
    }

    fun countAccessibleBooks(authorId: Long, accessibleLibraryIds: Set<Long>?): Int =
        (dsl.select(countDistinct(BOOK.ID))
            .from(AUTHOR)
            .join(bam).on(bam.AUTHOR_ID.eq(AUTHOR.ID))
            .join(BOOK).on(BOOK.ID.eq(bam.BOOK_ID))
            .where(AUTHOR.ID.eq(authorId))
            .and(BookConditions.notDeleted())
            .and(hasDigitalFile())
            .and(libraryCondition(accessibleLibraryIds, null))
            .fetchOne(0, Long::class.java) ?: 0L).toInt()

    /** Distinct author IDs matching the listing filter (used for filesystem photo filtering). */
    fun findMatchingAuthorIds(accessibleLibraryIds: Set<Long>?, libraryId: Long?, search: String?): List<Long> =
        dsl.selectDistinct(AUTHOR.ID)
            .from(AUTHOR)
            .join(bam).on(bam.AUTHOR_ID.eq(AUTHOR.ID))
            .join(BOOK).on(BOOK.ID.eq(bam.BOOK_ID))
            .where(baseFilter(accessibleLibraryIds, libraryId, search))
            .fetch(AUTHOR.ID)

    private fun baseFilter(accessibleLibraryIds: Set<Long>?, libraryId: Long?, search: String?): Condition {
        var condition = BookConditions.notDeleted()
            .and(hasDigitalFile())
            .and(libraryCondition(accessibleLibraryIds, libraryId))
        if (!search.isNullOrBlank()) {
            condition = condition.and(lower(AUTHOR.NAME).like("%${search.trim().lowercase()}%"))
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
        org.jooq.impl.DSL.exists(
            org.jooq.impl.DSL.selectOne().from(bf).where(bf.BOOK_ID.eq(BOOK.ID))
        )

    private fun authorSort(sortBy: String?, sortDir: String?): SortField<*> {
        val asc = "asc".equals(sortDir, ignoreCase = true)
        val field = when (sortBy?.lowercase()) {
            "bookcount", "book_count" -> countDistinct(BOOK.ID)
            "recent", "id" -> AUTHOR.ID
            else -> AUTHOR.NAME
        }
        return if (asc) field.asc() else field.desc()
    }
}
