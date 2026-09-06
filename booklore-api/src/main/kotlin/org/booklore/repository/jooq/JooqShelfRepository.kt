package org.booklore.repository.jooq

import org.booklore.jooq.tables.BookShelfMapping.BOOK_SHELF_MAPPING
import org.booklore.jooq.tables.Shelf.SHELF
import org.booklore.model.dto.Shelf
import org.booklore.model.enums.IconType
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.springframework.stereotype.Repository

/**
 * jOOQ read/write access to the book_shelf_mapping junction, replacing BookEntity.shelves /
 * ShelfEntity.bookEntities - two independent Hibernate @ManyToMany associations to the same table
 * with no mappedBy on either side. book_shelf_mapping has no extra columns (composite PK
 * (book_id, shelf_id)), so writes are plain existence-checked insert/delete, no master-row upsert
 * needed (unlike mood/category/tag/author, shelves themselves are a full aggregate root that stays
 * on Hibernate - this repo only owns the junction).
 */
@Repository
class JooqShelfRepository(private val dsl: DSLContext) {

    private val bsm = BOOK_SHELF_MAPPING

    /** Batched form of findShelvesByBookId, for mapping many books at once (e.g. a list response). */
    fun findShelvesByBookIds(bookIds: Collection<Long>): Map<Long, List<Shelf>> {
        if (bookIds.isEmpty()) return emptyMap()
        val bookCount = field(dsl.selectCount().from(bsm).where(bsm.SHELF_ID.eq(SHELF.ID)))
        return dsl.select(
            bsm.BOOK_ID, SHELF.ID, SHELF.NAME, SHELF.ICON, SHELF.ICON_TYPE,
            SHELF.USER_ID, SHELF.IS_PUBLIC, bookCount
        )
            .from(bsm)
            .join(SHELF).on(SHELF.ID.eq(bsm.SHELF_ID))
            .where(bsm.BOOK_ID.`in`(bookIds))
            .fetchGroups(bsm.BOOK_ID) { r ->
                Shelf.builder()
                    .id(r[SHELF.ID])
                    .name(r[SHELF.NAME])
                    .icon(r[SHELF.ICON])
                    .iconType(r[SHELF.ICON_TYPE]?.let { IconType.valueOf(it) })
                    .sort(null)
                    .userId(r[SHELF.USER_ID])
                    .publicShelf(r[SHELF.IS_PUBLIC] == 1.toByte())
                    .bookCount(r[bookCount])
                    .build()
            }
    }

    fun findShelvesByBookId(bookId: Long): Set<Shelf> =
        findShelvesByBookIds(listOf(bookId))[bookId]?.toSet() ?: emptySet()

    fun findShelfIdsByBookId(bookId: Long): Set<Long> =
        dsl.select(bsm.SHELF_ID).from(bsm).where(bsm.BOOK_ID.eq(bookId)).fetchSet(bsm.SHELF_ID)

    fun findBookIdsByShelfId(shelfId: Long): Set<Long> =
        dsl.select(bsm.BOOK_ID).from(bsm).where(bsm.SHELF_ID.eq(shelfId)).fetchSet(bsm.BOOK_ID)

    fun assignShelfToBook(bookId: Long, shelfId: Long) {
        val exists = dsl.fetchExists(
            dsl.selectOne().from(bsm).where(bsm.BOOK_ID.eq(bookId).and(bsm.SHELF_ID.eq(shelfId)))
        )
        if (!exists) {
            dsl.insertInto(bsm).set(bsm.BOOK_ID, bookId).set(bsm.SHELF_ID, shelfId).execute()
        }
    }

    fun unassignShelfFromBook(bookId: Long, shelfId: Long) {
        dsl.deleteFrom(bsm).where(bsm.BOOK_ID.eq(bookId).and(bsm.SHELF_ID.eq(shelfId))).execute()
    }

    /** Cross-product assign, skipping pairs that already exist (composite PK would reject a re-insert). */
    fun assignShelvesToBooks(bookIds: Collection<Long>, shelfIds: Collection<Long>) {
        if (bookIds.isEmpty() || shelfIds.isEmpty()) return
        val existing = dsl.select(bsm.BOOK_ID, bsm.SHELF_ID).from(bsm)
            .where(bsm.BOOK_ID.`in`(bookIds).and(bsm.SHELF_ID.`in`(shelfIds)))
            .fetch { it.value1()!! to it.value2()!! }
            .toSet()
        for (bookId in bookIds) {
            for (shelfId in shelfIds) {
                if ((bookId to shelfId) !in existing) {
                    dsl.insertInto(bsm).set(bsm.BOOK_ID, bookId).set(bsm.SHELF_ID, shelfId).execute()
                }
            }
        }
    }

    fun unassignShelvesFromBooks(bookIds: Collection<Long>, shelfIds: Collection<Long>) {
        if (bookIds.isEmpty() || shelfIds.isEmpty()) return
        dsl.deleteFrom(bsm).where(bsm.BOOK_ID.`in`(bookIds).and(bsm.SHELF_ID.`in`(shelfIds))).execute()
    }
}
