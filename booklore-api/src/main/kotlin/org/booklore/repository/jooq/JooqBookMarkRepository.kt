package org.booklore.repository.jooq

import org.booklore.jooq.tables.BookMarks.BOOK_MARKS
import org.booklore.jooq.tables.records.BookMarksRecord
import org.booklore.model.dto.BookMark
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqBookMarkRepository(private val dsl: DSLContext) {

    private val t = BOOK_MARKS

    fun findByBookIdAndUserIdOrderByPriorityAscCreatedAtDesc(bookId: Long, userId: Long): List<BookMark> =
        dsl.selectFrom(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .orderBy(t.PRIORITY.asc(), t.CREATED_AT.desc())
            .fetch()
            .map(::toDto)

    fun findByIdAndUserId(id: Long, userId: Long): BookMark? =
        dsl.selectFrom(t)
            .where(t.ID.eq(id).and(t.USER_ID.eq(userId)))
            .fetchOne()?.let(::toDto)

    fun existsByCfiAndBookIdAndUserId(cfi: String, bookId: Long, userId: Long, excludeId: Long? = null): Boolean {
        var condition = t.CFI.eq(cfi).and(t.BOOK_ID.eq(bookId)).and(t.USER_ID.eq(userId))
        if (excludeId != null) condition = condition.and(t.ID.ne(excludeId))
        return dsl.fetchExists(t, condition)
    }

    /** True if a bookmark exists within 5 seconds of positionMs (same track, or both track-less). */
    fun existsByPositionMsNearAndBookIdAndUserId(positionMs: Long, trackIndex: Int?, bookId: Long, userId: Long): Boolean {
        val trackCondition = if (trackIndex == null) t.TRACK_INDEX.isNull else t.TRACK_INDEX.eq(trackIndex)
        val condition = t.BOOK_ID.eq(bookId)
            .and(t.USER_ID.eq(userId))
            .and(t.POSITION_MS.isNotNull)
            .and(DSL.abs(t.POSITION_MS.minus(positionMs)).lt(5000L))
            .and(trackCondition)
        return dsl.fetchExists(t, condition)
    }

    fun insert(
        bookId: Long,
        userId: Long,
        cfi: String?,
        positionMs: Long?,
        trackIndex: Int?,
        title: String?,
        priority: Int?,
    ): BookMark {
        val now = LocalDateTime.now()
        val id = dsl.insertInto(t)
            .set(t.BOOK_ID, bookId)
            .set(t.USER_ID, userId)
            .set(t.CFI, cfi)
            .set(t.POSITION_MS, positionMs)
            .set(t.TRACK_INDEX, trackIndex)
            .set(t.TITLE, title)
            .set(t.PRIORITY, priority)
            .set(t.VERSION, 0L)
            .set(t.CREATED_AT, now)
            .set(t.UPDATED_AT, now)
            .returning(t.ID)
            .fetchOne()!!
            .id
        return findByIdAndUserId(id, userId)!!
    }

    /** Writes all mutable fields from the DTO (the service has already applied its partial updates), bumps version. */
    fun update(bookmark: BookMark): BookMark {
        dsl.update(t)
            .set(t.CFI, bookmark.cfi)
            .set(t.TITLE, bookmark.title)
            .set(t.COLOR, bookmark.color)
            .set(t.NOTES, bookmark.notes)
            .set(t.PRIORITY, bookmark.priority)
            .set(t.POSITION_MS, bookmark.positionMs)
            .set(t.TRACK_INDEX, bookmark.trackIndex)
            .set(t.VERSION, t.VERSION.plus(1L))
            .set(t.UPDATED_AT, LocalDateTime.now())
            .where(t.ID.eq(bookmark.id))
            .execute()
        return findByIdAndUserId(bookmark.id, bookmark.userId)!!
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    fun count(): Long = dsl.fetchCount(t).toLong()

    private fun toDto(r: BookMarksRecord): BookMark =
        BookMark.builder()
            .id(r.id)
            .userId(r.userId)
            .bookId(r.bookId)
            .cfi(r.cfi)
            .positionMs(r.get(t.POSITION_MS))
            .trackIndex(r.get(t.TRACK_INDEX))
            .title(r.title)
            .color(r.color)
            .notes(r.notes)
            .priority(r.get(t.PRIORITY))
            .createdAt(r.createdAt)
            .updatedAt(r.updatedAt)
            .build()
}
