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

    // ========================================================================
    // Mood master-row maintenance (consolidate/delete admin flows in MetadataManagementService).
    // ========================================================================

    /** Book ids whose mood junction references [name] (case-insensitive, matching JPA findByNameIgnoreCase). */
    fun findBookIdsByMoodNameIgnoreCase(name: String): List<Long> =
        dsl.select(bmm.BOOK_ID)
            .from(bmm).join(MOOD).on(MOOD.ID.eq(bmm.MOOD_ID))
            .where(MOOD.NAME.equalIgnoreCase(name))
            .fetch(bmm.BOOK_ID)

    /**
     * Ensures a mood master row exists with exactly this casing of [name]: renames an existing
     * case-insensitive match, or inserts a new row. Mirrors the JPA consolidate flow's
     * findByNameIgnoreCase(name).map(existing -> existing.setName(name)).orElseGet(create).
     */
    fun renameOrCreateMood(name: String) {
        val existingId = dsl.select(MOOD.ID).from(MOOD).where(MOOD.NAME.equalIgnoreCase(name)).fetchOne(MOOD.ID)
        if (existingId != null) {
            dsl.update(MOOD).set(MOOD.NAME, name).where(MOOD.ID.eq(existingId)).execute()
        } else {
            dsl.insertInto(MOOD).set(MOOD.NAME, name).execute()
        }
    }

    /** Deletes the mood master row by exact name. Call only once no book references it any more. */
    fun deleteMoodByName(name: String): Int =
        dsl.deleteFrom(MOOD).where(MOOD.NAME.eq(name)).execute()

    fun countAllMoods(): Long = dsl.fetchCount(MOOD).toLong()

    // ========================================================================
    // Writes — replace a book's category junction rows (masters are shared, upserted by name).
    // Replaces the JPA cascade of BookMetadataEntity.categories. Callers compute the final desired
    // set (merge/replace/clear semantics) and call setCategoriesForBook.
    // ========================================================================

    fun clearCategoriesForBook(bookId: Long) {
        dsl.deleteFrom(bcm).where(bcm.BOOK_ID.eq(bookId)).execute()
    }

    fun setCategoriesForBook(bookId: Long, categoryNames: Collection<String>) {
        dsl.deleteFrom(bcm).where(bcm.BOOK_ID.eq(bookId)).execute()
        categoryNames.asSequence()
            .filter { it.isNotBlank() }
            .map { upsertCategoryId(it) }
            .distinct()
            .forEach { categoryId ->
                dsl.insertInto(bcm).set(bcm.BOOK_ID, bookId).set(bcm.CATEGORY_ID, categoryId).execute()
            }
    }

    /** Find-or-create the category master row by name (name is UNIQUE), returning its id. */
    private fun upsertCategoryId(name: String): Long {
        val existing = dsl.select(CATEGORY.ID).from(CATEGORY).where(CATEGORY.NAME.eq(name)).fetchOne(CATEGORY.ID)
        if (existing != null) return existing
        return dsl.insertInto(CATEGORY).set(CATEGORY.NAME, name).returning(CATEGORY.ID).fetchOne()!!.id!!
    }

    // ========================================================================
    // Category master-row maintenance (consolidate/delete admin flows in MetadataManagementService).
    // ========================================================================

    /** Book ids whose category junction references [name] (case-insensitive, matching JPA findByNameIgnoreCase). */
    fun findBookIdsByCategoryNameIgnoreCase(name: String): List<Long> =
        dsl.select(bcm.BOOK_ID)
            .from(bcm).join(CATEGORY).on(CATEGORY.ID.eq(bcm.CATEGORY_ID))
            .where(CATEGORY.NAME.equalIgnoreCase(name))
            .fetch(bcm.BOOK_ID)

    /** Ensures a category master row exists with exactly this casing of [name] (rename or insert). */
    fun renameOrCreateCategory(name: String) {
        val existingId = dsl.select(CATEGORY.ID).from(CATEGORY).where(CATEGORY.NAME.equalIgnoreCase(name)).fetchOne(CATEGORY.ID)
        if (existingId != null) {
            dsl.update(CATEGORY).set(CATEGORY.NAME, name).where(CATEGORY.ID.eq(existingId)).execute()
        } else {
            dsl.insertInto(CATEGORY).set(CATEGORY.NAME, name).execute()
        }
    }

    /** Deletes the category master row by exact name. Call only once no book references it any more. */
    fun deleteCategoryByName(name: String): Int =
        dsl.deleteFrom(CATEGORY).where(CATEGORY.NAME.eq(name)).execute()

    fun countAllCategories(): Long = dsl.fetchCount(CATEGORY).toLong()

    // ========================================================================
    // Writes — replace a book's tag junction rows (masters are shared, upserted by name).
    // ========================================================================

    fun clearTagsForBook(bookId: Long) {
        dsl.deleteFrom(btm).where(btm.BOOK_ID.eq(bookId)).execute()
    }

    fun setTagsForBook(bookId: Long, tagNames: Collection<String>) {
        dsl.deleteFrom(btm).where(btm.BOOK_ID.eq(bookId)).execute()
        tagNames.asSequence()
            .filter { it.isNotBlank() }
            .map { upsertTagId(it) }
            .distinct()
            .forEach { tagId ->
                dsl.insertInto(btm).set(btm.BOOK_ID, bookId).set(btm.TAG_ID, tagId).execute()
            }
    }

    /** Find-or-create the tag master row by name (name is UNIQUE), returning its id. */
    private fun upsertTagId(name: String): Long {
        val existing = dsl.select(TAG.ID).from(TAG).where(TAG.NAME.eq(name)).fetchOne(TAG.ID)
        if (existing != null) return existing
        return dsl.insertInto(TAG).set(TAG.NAME, name).returning(TAG.ID).fetchOne()!!.id!!
    }

    // ========================================================================
    // Tag master-row maintenance (consolidate/delete admin flows in MetadataManagementService).
    // ========================================================================

    fun findBookIdsByTagNameIgnoreCase(name: String): List<Long> =
        dsl.select(btm.BOOK_ID)
            .from(btm).join(TAG).on(TAG.ID.eq(btm.TAG_ID))
            .where(TAG.NAME.equalIgnoreCase(name))
            .fetch(btm.BOOK_ID)

    fun renameOrCreateTag(name: String) {
        val existingId = dsl.select(TAG.ID).from(TAG).where(TAG.NAME.equalIgnoreCase(name)).fetchOne(TAG.ID)
        if (existingId != null) {
            dsl.update(TAG).set(TAG.NAME, name).where(TAG.ID.eq(existingId)).execute()
        } else {
            dsl.insertInto(TAG).set(TAG.NAME, name).execute()
        }
    }

    fun deleteTagByName(name: String): Int =
        dsl.deleteFrom(TAG).where(TAG.NAME.eq(name)).execute()

    fun countAllTags(): Long = dsl.fetchCount(TAG).toLong()
}
