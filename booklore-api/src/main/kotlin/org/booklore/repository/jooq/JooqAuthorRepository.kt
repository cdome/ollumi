package org.booklore.repository.jooq

import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.records.AuthorRecord
import org.booklore.repository.jooq.dto.AuthorRow
import org.booklore.repository.jooq.dto.AuthorSummaryRow
import org.jooq.DSLContext
import org.jooq.impl.DSL.countDistinct
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * jOOQ CRUD for the author master row (profile fields: description/asin/lock flags), replacing
 * AuthorRepository. Book<->author linking (the ordered junction with sort_order) is on
 * JooqBookMetadataRelationsRepository, matching where the read-side (findAuthorNamesByBookId) already
 * lives. This repo is the admin/profile surface used by AuthorMetadataService; the mobile app's
 * paginated/filtered listing stays on JooqAppAuthorRepository (Phase 8, unrelated).
 */
@Repository
class JooqAuthorRepository(private val dsl: DSLContext) {

    private val bam = BOOK_METADATA_AUTHOR_MAPPING

    fun findById(id: Long): Optional<AuthorRow> =
        Optional.ofNullable(dsl.selectFrom(AUTHOR).where(AUTHOR.ID.eq(id)).fetchOne()?.let(::toRow))

    fun findByNameIgnoreCase(name: String): Optional<AuthorRow> =
        Optional.ofNullable(dsl.selectFrom(AUTHOR).where(AUTHOR.NAME.equalIgnoreCase(name)).fetchOne()?.let(::toRow))

    /** Update if [row] has an id, insert otherwise; returns the persisted row (with id populated). */
    fun save(row: AuthorRow): AuthorRow {
        if (row.id != null) {
            dsl.update(AUTHOR)
                .set(AUTHOR.NAME, row.name)
                .set(AUTHOR.DESCRIPTION, row.description)
                .set(AUTHOR.ASIN, row.asin)
                .set(AUTHOR.NAME_LOCKED, if (row.nameLocked) 1.toByte() else 0.toByte())
                .set(AUTHOR.DESCRIPTION_LOCKED, if (row.descriptionLocked) 1.toByte() else 0.toByte())
                .set(AUTHOR.ASIN_LOCKED, if (row.asinLocked) 1.toByte() else 0.toByte())
                .set(AUTHOR.PHOTO_LOCKED, if (row.photoLocked) 1.toByte() else 0.toByte())
                .where(AUTHOR.ID.eq(row.id))
                .execute()
            return row
        }
        val id = dsl.insertInto(AUTHOR)
            .set(AUTHOR.NAME, row.name)
            .set(AUTHOR.DESCRIPTION, row.description)
            .set(AUTHOR.ASIN, row.asin)
            .set(AUTHOR.NAME_LOCKED, if (row.nameLocked) 1.toByte() else 0.toByte())
            .set(AUTHOR.DESCRIPTION_LOCKED, if (row.descriptionLocked) 1.toByte() else 0.toByte())
            .set(AUTHOR.ASIN_LOCKED, if (row.asinLocked) 1.toByte() else 0.toByte())
            .set(AUTHOR.PHOTO_LOCKED, if (row.photoLocked) 1.toByte() else 0.toByte())
            .returning(AUTHOR.ID)
            .fetchOne()!!.id!!
        row.id = id
        return row
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(AUTHOR).where(AUTHOR.ID.eq(id)).execute()
    }

    fun count(): Long = dsl.fetchCount(AUTHOR).toLong()

    /** Every author with its total (unfiltered) book count, for the desktop admin listing. */
    fun findAllWithBookCount(): List<AuthorSummaryRow> {
        val bookCount = countDistinct(bam.BOOK_ID)
        return dsl.select(AUTHOR.ID, AUTHOR.NAME, AUTHOR.ASIN, bookCount)
            .from(AUTHOR)
            .leftJoin(bam).on(bam.AUTHOR_ID.eq(AUTHOR.ID))
            .groupBy(AUTHOR.ID, AUTHOR.NAME, AUTHOR.ASIN)
            .orderBy(AUTHOR.NAME)
            .fetch { AuthorSummaryRow(it[AUTHOR.ID]!!, it[AUTHOR.NAME] ?: "", it[AUTHOR.ASIN], it[bookCount].toLong()) }
    }

    /** Same as [findAllWithBookCount] but counting only books in [libraryIds] (non-admin listing). */
    fun findAllWithBookCountByLibraryIds(libraryIds: Set<Long>): List<AuthorSummaryRow> {
        val bookCount = countDistinct(BOOK.ID)
        return dsl.select(AUTHOR.ID, AUTHOR.NAME, AUTHOR.ASIN, bookCount)
            .from(AUTHOR)
            .leftJoin(bam).on(bam.AUTHOR_ID.eq(AUTHOR.ID))
            .leftJoin(BOOK).on(BOOK.ID.eq(bam.BOOK_ID).and(BOOK.LIBRARY_ID.`in`(libraryIds)))
            .groupBy(AUTHOR.ID, AUTHOR.NAME, AUTHOR.ASIN)
            .orderBy(AUTHOR.NAME)
            .fetch { AuthorSummaryRow(it[AUTHOR.ID]!!, it[AUTHOR.NAME] ?: "", it[AUTHOR.ASIN], it[bookCount].toLong()) }
    }

    /** True if [authorId] has at least one book in one of [libraryIds] (non-admin access check). */
    fun existsByIdAndLibraryIds(authorId: Long, libraryIds: Set<Long>): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(bam)
                .join(BOOK).on(BOOK.ID.eq(bam.BOOK_ID))
                .where(bam.AUTHOR_ID.eq(authorId))
                .and(BOOK.LIBRARY_ID.`in`(libraryIds))
        )

    private fun toRow(r: AuthorRecord): AuthorRow = AuthorRow(
        id = r.id,
        name = r.name,
        description = r.description,
        asin = r.asin,
        nameLocked = r.nameLocked == 1.toByte(),
        descriptionLocked = r.descriptionLocked == 1.toByte(),
        asinLocked = r.asinLocked == 1.toByte(),
        photoLocked = r.photoLocked == 1.toByte(),
    )
}
