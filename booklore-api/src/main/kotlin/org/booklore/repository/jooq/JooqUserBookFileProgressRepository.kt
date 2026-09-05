package org.booklore.repository.jooq

import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.UserBookFileProgress.USER_BOOK_FILE_PROGRESS
import org.booklore.model.enums.BookFileType
import org.booklore.repository.jooq.dto.UserBookFileProgressRow
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

@Repository
class JooqUserBookFileProgressRepository(private val dsl: DSLContext) {

    private val t = USER_BOOK_FILE_PROGRESS
    private val bf = BOOK_FILE

    fun findByUserIdAndBookFileId(userId: Long, bookFileId: Long): Optional<UserBookFileProgressRow> =
        Optional.ofNullable(
            baseSelect()
                .where(t.USER_ID.eq(userId)).and(t.BOOK_FILE_ID.eq(bookFileId))
                .fetchOne()?.let(::toRow)
        )

    fun findByUserIdAndBookFileBookIdIn(userId: Long, bookIds: Collection<Long>): List<UserBookFileProgressRow> =
        if (bookIds.isEmpty()) emptyList()
        else baseSelect()
            .where(t.USER_ID.eq(userId)).and(bf.BOOK_ID.`in`(bookIds))
            .fetch().map(::toRow)

    fun deleteByUserIdAndBookIds(userId: Long, bookIds: Collection<Long>): Int =
        if (bookIds.isEmpty()) 0
        else dsl.deleteFrom(t)
            .where(t.USER_ID.eq(userId))
            .and(t.BOOK_FILE_ID.`in`(dsl.select(bf.ID).from(bf).where(bf.BOOK_ID.`in`(bookIds))))
            .execute()

    /** Upsert: UPDATE all columns when the row has an id, otherwise INSERT (and set the generated id). */
    fun save(row: UserBookFileProgressRow): UserBookFileProgressRow {
        val id = row.id
        if (id != null) {
            dsl.update(t)
                .set(t.USER_ID, row.userId)
                .set(t.BOOK_FILE_ID, row.bookFileId)
                .set(t.POSITION_DATA, row.positionData)
                .set(t.POSITION_HREF, row.positionHref)
                .set(t.PROGRESS_PERCENT, row.progressPercent?.toDouble())
                .set(t.TTS_POSITION_CFI, row.ttsPositionCfi)
                .set(t.LAST_READ_TIME, row.lastReadTime?.toLdt())
                .where(t.ID.eq(id))
                .execute()
        } else {
            row.id = dsl.insertInto(t)
                .set(t.USER_ID, row.userId)
                .set(t.BOOK_FILE_ID, row.bookFileId)
                .set(t.POSITION_DATA, row.positionData)
                .set(t.POSITION_HREF, row.positionHref)
                .set(t.PROGRESS_PERCENT, row.progressPercent?.toDouble())
                .set(t.TTS_POSITION_CFI, row.ttsPositionCfi)
                .set(t.LAST_READ_TIME, row.lastReadTime?.toLdt())
                .returning(t.ID)
                .fetchOne()!!.id
        }
        return row
    }

    private fun baseSelect() =
        dsl.select(
            t.ID, t.USER_ID, t.BOOK_FILE_ID, t.POSITION_DATA, t.POSITION_HREF,
            t.PROGRESS_PERCENT, t.TTS_POSITION_CFI, t.LAST_READ_TIME,
            bf.BOOK_ID, bf.BOOK_TYPE,
        )
            .from(t)
            .join(bf).on(t.BOOK_FILE_ID.eq(bf.ID))

    private fun toRow(r: Record) = UserBookFileProgressRow(
        id = r.get(t.ID),
        userId = r.get(t.USER_ID),
        bookFileId = r.get(t.BOOK_FILE_ID),
        bookId = r.get(bf.BOOK_ID),
        bookType = r.get(bf.BOOK_TYPE)?.let { BookFileType.valueOf(it) },
        positionData = r.get(t.POSITION_DATA),
        positionHref = r.get(t.POSITION_HREF),
        progressPercent = r.get(t.PROGRESS_PERCENT)?.toFloat(),
        ttsPositionCfi = r.get(t.TTS_POSITION_CFI),
        lastReadTime = r.get(t.LAST_READ_TIME)?.toInstant(),
    )

    private fun Instant.toLdt(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
    private fun LocalDateTime.toInstant(): Instant = this.toInstant(ZoneOffset.UTC)
}
