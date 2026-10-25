package org.booklore.repository.jooq

import org.booklore.jooq.tables.KoboLibrarySnapshotBook.KOBO_LIBRARY_SNAPSHOT_BOOK
import org.booklore.jooq.tables.KoboRemovedBooksTracking.KOBO_REMOVED_BOOKS_TRACKING
import org.booklore.jooq.tables.records.KoboLibrarySnapshotBookRecord
import org.booklore.repository.jooq.dto.KoboSnapshotBook
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

@Repository
class JooqKoboSnapshotBookRepository(private val dsl: DSLContext) {

    private val t = KOBO_LIBRARY_SNAPSHOT_BOOK

    fun findBySnapshotIdAndSyncedFalse(snapshotId: String, pageable: Pageable): Page<KoboSnapshotBook> =
        pagedByCondition(t.SNAPSHOT_ID.eq(snapshotId).and(t.SYNCED.eq(0.toByte())), pageable)

    fun markBooksSynced(snapshotId: String, bookIds: List<Long>) {
        if (bookIds.isEmpty()) return
        dsl.update(t)
            .set(t.SYNCED, 1.toByte())
            .where(t.SNAPSHOT_ID.eq(snapshotId).and(t.BOOK_ID.`in`(bookIds)))
            .execute()
    }

    /** curr-snapshot books not present in prev; when unsyncedOnly, restricted to synced=false. */
    fun findNewlyAddedBooks(prevSnapshotId: String, currSnapshotId: String, unsyncedOnly: Boolean, pageable: Pageable): Page<KoboSnapshotBook> {
        val prev = t.`as`("prev")
        var condition: Condition = t.SNAPSHOT_ID.eq(currSnapshotId)
            .and(t.BOOK_ID.notIn(DSL.select(prev.BOOK_ID).from(prev).where(prev.SNAPSHOT_ID.eq(prevSnapshotId))))
        if (unsyncedOnly) condition = condition.and(t.SYNCED.eq(0.toByte()))
        return pagedByCondition(condition, pageable)
    }

    /** prev-snapshot books absent from curr AND not already tracked as removed for curr. */
    fun findRemovedBooks(prevSnapshotId: String, currSnapshotId: String, pageable: Pageable): Page<KoboSnapshotBook> {
        val curr = t.`as`("curr")
        val condition = t.SNAPSHOT_ID.eq(prevSnapshotId)
            .and(t.BOOK_ID.notIn(DSL.select(curr.BOOK_ID).from(curr).where(curr.SNAPSHOT_ID.eq(currSnapshotId))))
            .and(t.BOOK_ID.notIn(
                DSL.select(KOBO_REMOVED_BOOKS_TRACKING.BOOK_ID_SYNCED)
                    .from(KOBO_REMOVED_BOOKS_TRACKING)
                    .where(KOBO_REMOVED_BOOKS_TRACKING.SNAPSHOT_ID.eq(currSnapshotId))
            ))
        return pagedByCondition(condition, pageable)
    }

    /** Books present in both snapshots with identical file hash and metadata timestamp. */
    fun findUnchangedBooksBetweenSnapshots(prevSnapshotId: String, currSnapshotId: String): List<KoboSnapshotBook> {
        val curr = t.`as`("curr")
        val prev = t.`as`("prev")
        return dsl.select(curr.ID, curr.SNAPSHOT_ID, curr.BOOK_ID, curr.FILE_HASH, curr.METADATA_UPDATED_AT, curr.SYNCED)
            .from(curr)
            .innerJoin(prev).on(curr.BOOK_ID.eq(prev.BOOK_ID))
            .where(curr.SNAPSHOT_ID.eq(currSnapshotId))
            .and(prev.SNAPSHOT_ID.eq(prevSnapshotId))
            .and(curr.FILE_HASH.eq(prev.FILE_HASH))
            .and(
                curr.METADATA_UPDATED_AT.eq(prev.METADATA_UPDATED_AT)
                    .or(curr.METADATA_UPDATED_AT.isNull.and(prev.METADATA_UPDATED_AT.isNull))
            )
            .fetch { r ->
                KoboSnapshotBook(
                    r.get(curr.ID), r.get(curr.SNAPSHOT_ID), r.get(curr.BOOK_ID), r.get(curr.FILE_HASH),
                    r.get(curr.METADATA_UPDATED_AT)?.toInstant(ZoneOffset.UTC), (r.get(curr.SYNCED) ?: 0).toInt() != 0,
                )
            }
    }

    /** Unsynced curr-snapshot books whose file hash or metadata timestamp differs from prev. */
    fun findChangedBooks(prevSnapshotId: String, currSnapshotId: String, pageable: Pageable): Page<KoboSnapshotBook> {
        val curr = t.`as`("curr")
        val prev = t.`as`("prev")
        val condition = curr.SNAPSHOT_ID.eq(currSnapshotId)
            .and(prev.SNAPSHOT_ID.eq(prevSnapshotId))
            .and(curr.SYNCED.eq(0.toByte()))
            .and(
                curr.FILE_HASH.ne(prev.FILE_HASH)
                    .or(curr.METADATA_UPDATED_AT.ne(prev.METADATA_UPDATED_AT)
                        .and(curr.METADATA_UPDATED_AT.isNotNull).and(prev.METADATA_UPDATED_AT.isNotNull))
                    .or(curr.METADATA_UPDATED_AT.isNotNull.and(prev.METADATA_UPDATED_AT.isNull))
            )

        val total = dsl.selectCount().from(curr).innerJoin(prev).on(curr.BOOK_ID.eq(prev.BOOK_ID))
            .where(condition).fetchOne(0, Long::class.java) ?: 0L
        val content = dsl.select(curr.ID, curr.SNAPSHOT_ID, curr.BOOK_ID, curr.FILE_HASH, curr.METADATA_UPDATED_AT, curr.SYNCED)
            .from(curr)
            .innerJoin(prev).on(curr.BOOK_ID.eq(prev.BOOK_ID))
            .where(condition)
            .orderBy(curr.ID)
            .limit(pageable.pageSize).offset(pageable.offset.toInt())
            .fetch { r ->
                KoboSnapshotBook(
                    r.get(curr.ID), r.get(curr.SNAPSHOT_ID), r.get(curr.BOOK_ID), r.get(curr.FILE_HASH),
                    r.get(curr.METADATA_UPDATED_AT)?.toInstant(ZoneOffset.UTC), (r.get(curr.SYNCED) ?: 0).toInt() != 0,
                )
            }
        return PageableHelper.toPage(content, total, pageable)
    }

    private fun pagedByCondition(condition: Condition, pageable: Pageable): Page<KoboSnapshotBook> {
        val total = dsl.fetchCount(t, condition).toLong()
        val content = dsl.selectFrom(t)
            .where(condition)
            .orderBy(t.ID)
            .limit(pageable.pageSize).offset(pageable.offset.toInt())
            .fetch()
            .map(::toDto)
        return PageableHelper.toPage(content, total, pageable)
    }

    private fun toDto(r: KoboLibrarySnapshotBookRecord): KoboSnapshotBook =
        KoboSnapshotBook(
            id = r.id,
            snapshotId = r.snapshotId,
            bookId = r.bookId,
            fileHash = r.fileHash,
            metadataUpdatedAt = r.get(t.METADATA_UPDATED_AT)?.toInstant(ZoneOffset.UTC),
            synced = (r.get(t.SYNCED) ?: 0).toInt() != 0,
        )
}
