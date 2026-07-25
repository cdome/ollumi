package org.booklore.repository.jooq

import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.booklore.model.enums.ReadStatus
import org.booklore.repository.jooq.dto.BookCompletionHeatmapEntry
import org.booklore.repository.jooq.dto.CompletionTimelineEntry
import org.booklore.repository.jooq.dto.ProgressPercents
import org.booklore.repository.jooq.dto.RatingDistributionEntry
import org.booklore.repository.jooq.dto.StatusDistributionEntry
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class JooqUserBookProgressRepository(private val dsl: DSLContext) {

    private val ubp = USER_BOOK_PROGRESS

    // ========================================================================
    // Statistics
    // ========================================================================

    fun findCompletionTimelineByUser(userId: Long, year: Int): List<CompletionTimelineEntry> {
        val completedAt = coalesce(ubp.DATE_FINISHED, ubp.READ_STATUS_MODIFIED_TIME, ubp.LAST_READ_TIME)
        val yearField = year(completedAt)
        val monthField = month(completedAt)
        val bookCount = count()
        return dsl.select(yearField, monthField, ubp.READ_STATUS, bookCount)
            .from(ubp)
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.READ_STATUS.isNotNull)
            .and(ubp.READ_STATUS.notIn(ReadStatus.UNSET.name, ReadStatus.UNREAD.name))
            .and(completedAt.isNotNull)
            .and(yearField.eq(year))
            .groupBy(yearField, monthField, ubp.READ_STATUS)
            .orderBy(yearField.desc(), monthField.desc())
            .fetch { record ->
                CompletionTimelineEntry(
                    year = record[yearField]!!,
                    month = record[monthField]!!,
                    readStatus = ReadStatus.valueOf(record[ubp.READ_STATUS]!!),
                    bookCount = record[bookCount].toLong()
                )
            }
    }

    fun findBookCompletionHeatmap(userId: Long, startYear: Int, endYear: Int): List<BookCompletionHeatmapEntry> {
        val yearField = year(ubp.DATE_FINISHED)
        val monthField = month(ubp.DATE_FINISHED)
        val bookCount = count()
        return dsl.select(yearField, monthField, bookCount)
            .from(ubp)
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.DATE_FINISHED.isNotNull)
            .and(yearField.ge(startYear))
            .and(yearField.le(endYear))
            .groupBy(yearField, monthField)
            .orderBy(yearField.asc(), monthField.asc())
            .fetch { record ->
                BookCompletionHeatmapEntry(
                    year = record[yearField]!!,
                    month = record[monthField]!!,
                    count = record[bookCount].toLong()
                )
            }
    }

    fun findRatingDistributionByUser(userId: Long): List<RatingDistributionEntry> {
        val bookCount = count()
        return dsl.select(ubp.PERSONAL_RATING, bookCount)
            .from(ubp)
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.PERSONAL_RATING.isNotNull)
            .groupBy(ubp.PERSONAL_RATING)
            .orderBy(ubp.PERSONAL_RATING)
            .fetch { record ->
                RatingDistributionEntry(
                    rating = record[ubp.PERSONAL_RATING]!!.toInt(),
                    count = record[bookCount].toLong()
                )
            }
    }

    fun findStatusDistributionByUser(userId: Long): List<StatusDistributionEntry> {
        val bookCount = count()
        return dsl.select(ubp.READ_STATUS, bookCount)
            .from(ubp)
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.READ_STATUS.isNotNull)
            .and(ubp.READ_STATUS.ne(ReadStatus.UNSET.name))
            .groupBy(ubp.READ_STATUS)
            .fetch { record ->
                StatusDistributionEntry(
                    status = ReadStatus.valueOf(record[ubp.READ_STATUS]!!),
                    count = record[bookCount].toLong()
                )
            }
    }

    fun findAllProgressPercentsByUser(userId: Long): List<ProgressPercents> =
        dsl.select(
            ubp.KOREADER_PROGRESS_PERCENT, ubp.KOBO_PROGRESS_PERCENT,
            ubp.EPUB_PROGRESS_PERCENT, ubp.PDF_PROGRESS_PERCENT, ubp.CBX_PROGRESS_PERCENT
        )
            .from(ubp)
            .where(ubp.USER_ID.eq(userId))
            .fetch { record ->
                ProgressPercents(
                    koreaderProgressPercent = record[ubp.KOREADER_PROGRESS_PERCENT]?.toFloat(),
                    koboProgressPercent = record[ubp.KOBO_PROGRESS_PERCENT]?.toFloat(),
                    epubProgressPercent = record[ubp.EPUB_PROGRESS_PERCENT]?.toFloat(),
                    pdfProgressPercent = record[ubp.PDF_PROGRESS_PERCENT]?.toFloat(),
                    cbxProgressPercent = record[ubp.CBX_PROGRESS_PERCENT]?.toFloat()
                )
            }

    // ========================================================================
    // Progress book IDs
    // ========================================================================

    fun findExistingProgressBookIds(userId: Long, bookIds: Collection<Long>): Set<Long> =
        dsl.select(ubp.BOOK_ID)
            .from(ubp)
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.BOOK_ID.`in`(bookIds))
            .fetchSet(ubp.BOOK_ID)

    // ========================================================================
    // Bulk writes
    // ========================================================================

    fun bulkUpdateReadStatus(
        userId: Long,
        bookIds: Collection<Long>,
        readStatus: ReadStatus?,
        modifiedTime: Instant,
        dateFinished: Instant?
    ): Int =
        dsl.update(ubp)
            .set(ubp.READ_STATUS, readStatus?.name)
            .set(ubp.READ_STATUS_MODIFIED_TIME, modifiedTime.toUtcLocalDateTime())
            .set(ubp.DATE_FINISHED, dateFinished?.toUtcLocalDateTime())
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.BOOK_ID.`in`(bookIds))
            .execute()

    fun bulkResetBookloreProgress(userId: Long, bookIds: Collection<Long>, modifiedTime: Instant): Int =
        dsl.update(ubp)
            .setNull(ubp.READ_STATUS)
            .set(ubp.READ_STATUS_MODIFIED_TIME, modifiedTime.toUtcLocalDateTime())
            .setNull(ubp.LAST_READ_TIME)
            .setNull(ubp.DATE_FINISHED)
            .setNull(ubp.PDF_PROGRESS)
            .setNull(ubp.PDF_PROGRESS_PERCENT)
            .setNull(ubp.EPUB_PROGRESS)
            .setNull(ubp.EPUB_PROGRESS_PERCENT)
            .setNull(ubp.CBX_PROGRESS)
            .setNull(ubp.CBX_PROGRESS_PERCENT)
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.BOOK_ID.`in`(bookIds))
            .execute()

    fun bulkResetKoreaderProgress(userId: Long, bookIds: Collection<Long>): Int =
        dsl.update(ubp)
            .setNull(ubp.KOREADER_PROGRESS)
            .setNull(ubp.KOREADER_PROGRESS_PERCENT)
            .setNull(ubp.KOREADER_DEVICE_ID)
            .setNull(ubp.KOREADER_DEVICE)
            .setNull(ubp.KOREADER_LAST_SYNC_TIME)
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.BOOK_ID.`in`(bookIds))
            .execute()

    fun bulkResetKoboProgress(userId: Long, bookIds: Collection<Long>): Int =
        dsl.update(ubp)
            .setNull(ubp.KOBO_PROGRESS_PERCENT)
            .setNull(ubp.KOBO_LOCATION)
            .setNull(ubp.KOBO_LOCATION_TYPE)
            .setNull(ubp.KOBO_LOCATION_SOURCE)
            .setNull(ubp.KOBO_PROGRESS_RECEIVED_TIME)
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.BOOK_ID.`in`(bookIds))
            .execute()

    fun bulkUpdatePersonalRating(userId: Long, bookIds: Collection<Long>, rating: Int?): Int =
        dsl.update(ubp)
            .set(ubp.PERSONAL_RATING, rating?.toByte())
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.BOOK_ID.`in`(bookIds))
            .execute()

    private fun Instant.toUtcLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
