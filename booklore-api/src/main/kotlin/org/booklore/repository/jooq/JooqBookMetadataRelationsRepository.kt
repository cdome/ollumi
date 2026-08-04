package org.booklore.repository.jooq

import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookMetadataCategoryMapping.BOOK_METADATA_CATEGORY_MAPPING
import org.booklore.jooq.tables.BookMetadataMoodMapping.BOOK_METADATA_MOOD_MAPPING
import org.booklore.jooq.tables.BookMetadataTagMapping.BOOK_METADATA_TAG_MAPPING
import org.booklore.jooq.tables.Category.CATEGORY
import org.booklore.jooq.tables.Mood.MOOD
import org.booklore.jooq.tables.Tag.TAG
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/**
 * jOOQ read access to a book's metadata @ManyToMany relation NAMES (authors/categories/tags/moods),
 * replacing entity-collection reads (BookMetadataEntity.getAuthors()/getCategories()/...). Used to move
 * consumers off the JPA relations ahead of dropping those collections from BookMetadataEntity.
 *
 * Junctions key on book_id (== book_metadata.book_id == book.id, a shared PK). Authors carry a
 * persisted sort_order and are returned as an ordered List; categories/tags/moods are unordered Sets.
 */
@Repository
class JooqBookMetadataRelationsRepository(private val dsl: DSLContext) {

    private val bam = BOOK_METADATA_AUTHOR_MAPPING
    private val bcm = BOOK_METADATA_CATEGORY_MAPPING
    private val btm = BOOK_METADATA_TAG_MAPPING
    private val bmm = BOOK_METADATA_MOOD_MAPPING

    fun findAuthorNamesByBookId(bookId: Long): List<String> =
        dsl.select(AUTHOR.NAME)
            .from(bam).join(AUTHOR).on(AUTHOR.ID.eq(bam.AUTHOR_ID))
            .where(bam.BOOK_ID.eq(bookId))
            .orderBy(bam.SORT_ORDER)
            .fetch(AUTHOR.NAME)

    fun findCategoryNamesByBookId(bookId: Long): Set<String> =
        dsl.select(CATEGORY.NAME)
            .from(bcm).join(CATEGORY).on(CATEGORY.ID.eq(bcm.CATEGORY_ID))
            .where(bcm.BOOK_ID.eq(bookId))
            .fetchSet(CATEGORY.NAME)

    fun findTagNamesByBookId(bookId: Long): Set<String> =
        dsl.select(TAG.NAME)
            .from(btm).join(TAG).on(TAG.ID.eq(btm.TAG_ID))
            .where(btm.BOOK_ID.eq(bookId))
            .fetchSet(TAG.NAME)

    fun findMoodNamesByBookId(bookId: Long): Set<String> =
        dsl.select(MOOD.NAME)
            .from(bmm).join(MOOD).on(MOOD.ID.eq(bmm.MOOD_ID))
            .where(bmm.BOOK_ID.eq(bookId))
            .fetchSet(MOOD.NAME)

    // Batched variants (bookId -> names) for consumers that filter/process a list of books, to avoid N+1.

    fun findAuthorNamesByBookIds(bookIds: Collection<Long>): Map<Long, List<String>> {
        if (bookIds.isEmpty()) return emptyMap()
        return dsl.select(bam.BOOK_ID, AUTHOR.NAME)
            .from(bam).join(AUTHOR).on(AUTHOR.ID.eq(bam.AUTHOR_ID))
            .where(bam.BOOK_ID.`in`(bookIds))
            .orderBy(bam.BOOK_ID, bam.SORT_ORDER)
            .fetchGroups(bam.BOOK_ID, AUTHOR.NAME)
    }

    fun findCategoryNamesByBookIds(bookIds: Collection<Long>): Map<Long, Set<String>> {
        if (bookIds.isEmpty()) return emptyMap()
        return dsl.select(bcm.BOOK_ID, CATEGORY.NAME)
            .from(bcm).join(CATEGORY).on(CATEGORY.ID.eq(bcm.CATEGORY_ID))
            .where(bcm.BOOK_ID.`in`(bookIds))
            .fetchGroups(bcm.BOOK_ID, CATEGORY.NAME)
            .mapValues { it.value.toSet() }
    }

    fun findTagNamesByBookIds(bookIds: Collection<Long>): Map<Long, Set<String>> {
        if (bookIds.isEmpty()) return emptyMap()
        return dsl.select(btm.BOOK_ID, TAG.NAME)
            .from(btm).join(TAG).on(TAG.ID.eq(btm.TAG_ID))
            .where(btm.BOOK_ID.`in`(bookIds))
            .fetchGroups(btm.BOOK_ID, TAG.NAME)
            .mapValues { it.value.toSet() }
    }

    fun findMoodNamesByBookIds(bookIds: Collection<Long>): Map<Long, Set<String>> {
        if (bookIds.isEmpty()) return emptyMap()
        return dsl.select(bmm.BOOK_ID, MOOD.NAME)
            .from(bmm).join(MOOD).on(MOOD.ID.eq(bmm.MOOD_ID))
            .where(bmm.BOOK_ID.`in`(bookIds))
            .fetchGroups(bmm.BOOK_ID, MOOD.NAME)
            .mapValues { it.value.toSet() }
    }

    // ========================================================================
    // Writes — replace a book's mood junction rows (masters are shared, upserted by name).
    // Replaces the JPA cascade of BookMetadataEntity.moods. Callers compute the final desired
    // set (merge/replace/clear semantics) and call setMoodsForBook.
    // ========================================================================

    fun clearMoodsForBook(bookId: Long) {
        dsl.deleteFrom(bmm).where(bmm.BOOK_ID.eq(bookId)).execute()
    }

    fun setMoodsForBook(bookId: Long, moodNames: Collection<String>) {
        dsl.deleteFrom(bmm).where(bmm.BOOK_ID.eq(bookId)).execute()
        moodNames.asSequence()
            .filter { it.isNotBlank() }
            .map { upsertMoodId(it) }
            .distinct()
            .forEach { moodId ->
                dsl.insertInto(bmm).set(bmm.BOOK_ID, bookId).set(bmm.MOOD_ID, moodId).execute()
            }
    }

    /** Find-or-create the mood master row by name (name is UNIQUE), returning its id. */
    private fun upsertMoodId(name: String): Long {
        val existing = dsl.select(MOOD.ID).from(MOOD).where(MOOD.NAME.eq(name)).fetchOne(MOOD.ID)
        if (existing != null) return existing
        return dsl.insertInto(MOOD).set(MOOD.NAME, name).returning(MOOD.ID).fetchOne()!!.id!!
    }
}
