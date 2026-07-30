package org.booklore.repository.jooq

import org.booklore.jooq.tables.KoboRemovedBooksTracking.KOBO_REMOVED_BOOKS_TRACKING
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/** Tracks book ids whose Kobo progress was removed in a given sync snapshot (table kobo_removed_books_tracking). */
@Repository
class JooqKoboDeletedBookProgressRepository(private val dsl: DSLContext) {

    private val t = KOBO_REMOVED_BOOKS_TRACKING

    fun insertAll(snapshotId: String, userId: Long, bookIds: List<Long>) {
        if (bookIds.isEmpty()) return
        val step = dsl.insertInto(t, t.SNAPSHOT_ID, t.USER_ID, t.BOOK_ID_SYNCED)
        bookIds.forEach { step.values(snapshotId, userId, it) }
        step.execute()
    }

    fun deleteBySnapshotIdAndUserId(snapshotId: String, userId: Long) {
        dsl.deleteFrom(t)
            .where(t.SNAPSHOT_ID.eq(snapshotId).and(t.USER_ID.eq(userId)))
            .execute()
    }
}
