package org.booklore.repository.jooq

import org.booklore.jooq.tables.BookShelfMapping.BOOK_SHELF_MAPPING
import org.booklore.jooq.tables.Shelf.SHELF
import org.booklore.model.dto.Shelf
import org.booklore.model.dto.Sort
import org.booklore.model.entity.BookLoreUserEntity
import org.booklore.model.entity.ShelfEntity
import org.booklore.model.enums.IconType
import org.booklore.model.enums.SortDirection
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL.field
import org.springframework.stereotype.Repository

/**
 * jOOQ access to the shelf table (ShelfEntity CRUD, replacing ShelfRepository) and its
 * book_shelf_mapping junction, replacing BookEntity.shelves / ShelfEntity.bookEntities - two
 * independent Hibernate @ManyToMany associations to the same table with no mappedBy on either side.
 * book_shelf_mapping has no extra columns (composite PK (book_id, shelf_id)), so junction writes are
 * plain existence-checked insert/delete, no master-row upsert needed.
 */
@Repository
class JooqShelfRepository(private val dsl: DSLContext) {

    private val bsm = BOOK_SHELF_MAPPING

    // ========================================================================
    // ShelfEntity CRUD - replacing ShelfRepository. Every method attaches bookCount (the former
    // Hibernate @Formula correlated subquery, replicated below) and `user` as an id-only stub -
    // traced every consumer's downstream use of the returned entity and none ever reads more than
    // shelf.getUser().getId(), while bookCount and user.id both flow through ShelfMapper.toShelf /
    // AppBookMapper.toShelfSummaryFromEntity for nearly every call site.
    // ========================================================================

    private val bookCountField = field(dsl.selectCount().from(bsm).where(bsm.SHELF_ID.eq(SHELF.ID)))

    private fun selectShelves() =
        dsl.select(SHELF.ID, SHELF.USER_ID, SHELF.NAME, SHELF.SORT, SHELF.ICON, SHELF.ICON_TYPE, SHELF.IS_PUBLIC, bookCountField)
            .from(SHELF)

    private fun toSort(raw: String?): Sort? {
        if (raw.isNullOrEmpty()) return null
        val parts = raw.split(",")
        val sort = Sort()
        sort.field = parts[0]
        sort.direction = SortDirection.valueOf(parts[1])
        return sort
    }

    private fun mapRow(r: Record): ShelfEntity =
        ShelfEntity.builder()
            .id(r[SHELF.ID])
            .user(BookLoreUserEntity.builder().id(r[SHELF.USER_ID]).build())
            .name(r[SHELF.NAME])
            .sort(toSort(r[SHELF.SORT]))
            .icon(r[SHELF.ICON])
            .iconType(r[SHELF.ICON_TYPE]?.let { IconType.valueOf(it) })
            .isPublic(r[SHELF.IS_PUBLIC] == 1.toByte())
            .bookCount(r[bookCountField])
            .build()

    fun findById(id: Long): ShelfEntity? =
        selectShelves().where(SHELF.ID.eq(id)).fetchOne()?.let(::mapRow)

    /** Mirrors ShelfRepository.findByIdWithUser: every findById here already attaches user, so this
     * is the same lookup under its old name. */
    fun findByIdWithUser(id: Long): ShelfEntity? = findById(id)

    fun findByUserId(userId: Long): List<ShelfEntity> =
        selectShelves().where(SHELF.USER_ID.eq(userId)).fetch().map(::mapRow)

    fun findByUserIdAndName(userId: Long, name: String): ShelfEntity? =
        selectShelves().where(SHELF.USER_ID.eq(userId).and(SHELF.NAME.eq(name))).fetchOne()?.let(::mapRow)

    fun findByUserIdOrPublicShelfTrue(userId: Long): List<ShelfEntity> =
        selectShelves().where(SHELF.USER_ID.eq(userId).or(SHELF.IS_PUBLIC.eq(1))).fetch().map(::mapRow)

    fun findByUserIdInAndName(userIds: Collection<Long>, name: String): List<ShelfEntity> {
        if (userIds.isEmpty()) return emptyList()
        return selectShelves().where(SHELF.USER_ID.`in`(userIds).and(SHELF.NAME.eq(name))).fetch().map(::mapRow)
    }

    fun existsByUserIdAndName(userId: Long, name: String): Boolean =
        dsl.fetchExists(dsl.selectOne().from(SHELF).where(SHELF.USER_ID.eq(userId).and(SHELF.NAME.eq(name))))

    fun count(): Long = dsl.fetchCount(SHELF).toLong()

    fun deleteById(id: Long) {
        dsl.deleteFrom(SHELF).where(SHELF.ID.eq(id)).execute()
    }

    /** Mirrors ShelfRepository.save: insert-or-update based on whether [shelf] already carries an id,
     * matching JPA save() semantics. Returns the same object (id populated on insert). bookCount is
     * never recomputed here: on insert a brand-new shelf has zero books by definition; on update the
     * caller's shelf was fetched via findById/findByIdWithUser moments earlier in the same
     * transaction and this method never touches book_shelf_mapping, so its bookCount stays accurate. */
    fun save(shelf: ShelfEntity): ShelfEntity {
        val sortStr = shelf.sort?.let { "${it.field},${it.direction.name}" }
        val isPublicByte = if (shelf.isPublic) 1.toByte() else 0.toByte()
        val existingId = shelf.id
        if (existingId == null) {
            val newId = dsl.insertInto(SHELF)
                .set(SHELF.USER_ID, shelf.user.id)
                .set(SHELF.NAME, shelf.name)
                .set(SHELF.SORT, sortStr)
                .set(SHELF.ICON, shelf.icon)
                .set(SHELF.ICON_TYPE, shelf.iconType?.name)
                .set(SHELF.IS_PUBLIC, isPublicByte)
                .returning(SHELF.ID)
                .fetchOne()!!.id!!
            shelf.id = newId
        } else {
            dsl.update(SHELF)
                .set(SHELF.USER_ID, shelf.user.id)
                .set(SHELF.NAME, shelf.name)
                .set(SHELF.SORT, sortStr)
                .set(SHELF.ICON, shelf.icon)
                .set(SHELF.ICON_TYPE, shelf.iconType?.name)
                .set(SHELF.IS_PUBLIC, isPublicByte)
                .where(SHELF.ID.eq(existingId))
                .execute()
        }
        return shelf
    }

    // ========================================================================
    // book_shelf_mapping junction - unchanged from before this slice.
    // ========================================================================

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
