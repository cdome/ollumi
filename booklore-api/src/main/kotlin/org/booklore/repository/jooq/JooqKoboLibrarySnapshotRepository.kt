package org.booklore.repository.jooq

import org.booklore.jooq.tables.KoboLibrarySnapshot.KOBO_LIBRARY_SNAPSHOT
import org.booklore.jooq.tables.KoboLibrarySnapshotBook.KOBO_LIBRARY_SNAPSHOT_BOOK
import org.booklore.jooq.tables.records.KoboLibrarySnapshotRecord
import org.booklore.repository.jooq.dto.KoboLibrarySnapshot
import org.booklore.repository.jooq.dto.KoboSnapshotBook
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class JooqKoboLibrarySnapshotRepository(private val dsl: DSLContext) {

    private val s = KOBO_LIBRARY_SNAPSHOT
    private val b = KOBO_LIBRARY_SNAPSHOT_BOOK

    fun findByIdAndUserId(id: String, userId: Long): KoboLibrarySnapshot? =
        dsl.selectFrom(s).where(s.ID.eq(id).and(s.USER_ID.eq(userId))).fetchOne()?.let(::toDto)

    /** Insert the snapshot row and its snapshot-book children (was a JPA cascade save). */
    fun insert(id: String, userId: Long, createdDate: LocalDateTime, books: List<KoboSnapshotBook>): KoboLibrarySnapshot {
        dsl.insertInto(s)
            .set(s.ID, id)
            .set(s.USER_ID, userId)
            .set(s.CREATED_DATE, createdDate)
            .execute()
        if (books.isNotEmpty()) {
            val step = dsl.insertInto(b, b.SNAPSHOT_ID, b.BOOK_ID, b.FILE_HASH, b.METADATA_UPDATED_AT, b.SYNCED)
            books.forEach {
                step.values(
                    id,
                    it.bookId,
                    it.fileHash,
                    it.metadataUpdatedAt?.let { ts -> LocalDateTime.ofInstant(ts, ZoneOffset.UTC) },
                    if (it.synced) 1.toByte() else 0.toByte(),
                )
            }
            step.execute()
        }
        return KoboLibrarySnapshot(id, userId, createdDate)
    }

    /** Deletes the snapshot; the child kobo_library_snapshot_book rows go via the DB ON DELETE CASCADE FK. */
    fun deleteById(id: String) {
        dsl.deleteFrom(s).where(s.ID.eq(id)).execute()
    }

    private fun toDto(r: KoboLibrarySnapshotRecord): KoboLibrarySnapshot =
        KoboLibrarySnapshot(r.id, r.userId, r.createdDate)
}
