package org.booklore.repository.jooq

import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookMetadataCategoryMapping.BOOK_METADATA_CATEGORY_MAPPING
import org.booklore.jooq.tables.Category.CATEGORY
import org.booklore.jooq.tables.ReadingSessions.READING_SESSIONS
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.booklore.repository.jooq.dto.*
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class JooqReadingSessionRepository(private val dsl: DSLContext) {

    private val rs = READING_SESSIONS
    private val bm = BOOK_METADATA
    private val ubp = USER_BOOK_PROGRESS
    private val bf = BOOK_FILE

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun convertTz(field: Field<*>, tzOffset: String): Field<LocalDateTime> =
        function("CONVERT_TZ", LocalDateTime::class.java, field, inline("+00:00"), inline(tzOffset))

    private fun dateOf(field: Field<*>): Field<LocalDate> =
        function("DATE", LocalDate::class.java, field)

    // ========================================================================
    // Reading heatmap / session counts
    // ========================================================================

    fun findSessionCountsByUserAndYear(userId: Long, year: Int, tzOffset: String): List<ReadingSessionCount> {
        val localTime = convertTz(rs.START_TIME, tzOffset)
        val dateField = dateOf(localTime)
        val cnt = count()
        return dsl.select(dateField, cnt)
            .from(rs)
            .where(rs.USER_ID.eq(userId))
            .and(year(localTime).eq(year))
            .groupBy(dateField)
            .orderBy(dateField)
            .fetch { r -> ReadingSessionCount(r.get(dateField)!!, r.get(cnt)!!.toLong()) }
    }

    fun findSessionCountsByUserAndYearAndMonth(
        userId: Long,
        year: Int,
        month: Int,
        tzOffset: String
    ): List<ReadingSessionCount> {
        val localTime = convertTz(rs.START_TIME, tzOffset)
        val dateField = dateOf(localTime)
        val cnt = count()
        return dsl.select(dateField, cnt)
            .from(rs)
            .where(rs.USER_ID.eq(userId))
            .and(year(localTime).eq(year))
            .and(month(localTime).eq(month))
            .groupBy(dateField)
            .orderBy(dateField)
            .fetch { r -> ReadingSessionCount(r.get(dateField)!!, r.get(cnt)!!.toLong()) }
    }

    fun findAllSessionCountsByUser(userId: Long, tzOffset: String): List<ReadingSessionCount> {
        val localTime = convertTz(rs.START_TIME, tzOffset)
        val dateField = dateOf(localTime)
        val cnt = count()
        return dsl.select(dateField, cnt)
            .from(rs)
            .where(rs.USER_ID.eq(userId))
            .groupBy(dateField)
            .orderBy(dateField)
            .fetch { r -> ReadingSessionCount(r.get(dateField)!!, r.get(cnt)!!.toLong()) }
    }

    // ========================================================================
    // Session timeline (week view)
    // ========================================================================

    fun findSessionTimelineByUserAndWeek(
        userId: Long,
        startOfWeek: java.time.Instant,
        endOfWeek: java.time.Instant
    ): List<ReadingSessionTimeline> {
        val startLdt = LocalDateTime.ofInstant(startOfWeek, ZoneOffset.UTC)
        val endLdt = LocalDateTime.ofInstant(endOfWeek, ZoneOffset.UTC)

        val bookTitle = coalesce(
            bm.TITLE,
            dsl.select(bf.FILE_NAME)
                .from(bf)
                .where(bf.BOOK_ID.eq(BOOK.ID))
                .orderBy(bf.ID.asc())
                .limit(1)
                .asField<String>(),
            inline("Unknown Book")
        )

        return dsl.select(
            BOOK.ID,
            bookTitle,
            rs.BOOK_TYPE,
            rs.START_TIME,
            rs.END_TIME,
            rs.DURATION_SECONDS
        )
            .from(rs)
            .join(BOOK).on(rs.BOOK_ID.eq(BOOK.ID))
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(rs.USER_ID.eq(userId))
            .and(rs.START_TIME.ge(startLdt))
            .and(rs.START_TIME.lt(endLdt))
            .orderBy(rs.START_TIME)
            .fetch { r ->
                ReadingSessionTimeline(
                    bookId = r.get(BOOK.ID)!!,
                    bookTitle = r.get(bookTitle)!!,
                    bookFileType = r.get(rs.BOOK_TYPE)!!,
                    startDate = r.get(rs.START_TIME)!!,
                    endDate = r.get(rs.END_TIME)!!,
                    totalSessions = 1L,
                    totalDurationSeconds = r.get(rs.DURATION_SECONDS)!!.toLong()
                )
            }
    }

    // ========================================================================
    // Reading speed
    // ========================================================================

    fun findReadingSpeedByUserAndYear(userId: Long, year: Int): List<ReadingSpeed> {
        val dateField = dateOf(rs.CREATED_AT)
        val avgProgress = avg(
            rs.PROGRESS_DELTA.div(rs.DURATION_SECONDS.cast(Double::class.java).div(inline(60.0)))
        )
        val totalSessions = count()

        return dsl.select(dateField, avgProgress, totalSessions)
            .from(rs)
            .where(rs.USER_ID.eq(userId))
            .and(rs.DURATION_SECONDS.gt(0))
            .and(rs.PROGRESS_DELTA.gt(0.0))
            .and(year(rs.CREATED_AT).eq(year))
            .groupBy(dateField)
            .orderBy(dateField)
            .fetch { r ->
                ReadingSpeed(
                    date = r.get(dateField)!!,
                    avgProgressPerMinute = r.get(avgProgress)?.toDouble() ?: 0.0,
                    totalSessions = r.get(totalSessions)!!
                )
            }
    }

    // ========================================================================
    // Peak reading hours
    // ========================================================================

    fun findPeakReadingHoursByUser(userId: Long, year: Int?, month: Int?, tzOffset: String): List<PeakReadingHour> =
        findPeakHours(userId, year, month, tzOffset, bookTypeFilter = null)

    // ========================================================================
    // Favorite reading days
    // ========================================================================

    fun findFavoriteReadingDaysByUser(
        userId: Long,
        year: Int?,
        month: Int?,
        tzOffset: String
    ): List<FavoriteReadingDay> {
        val localTime = convertTz(rs.START_TIME, tzOffset)
        val dowField = dayOfWeek(localTime)
        val sessionCount = count()
        val totalDuration = coalesce(sum(rs.DURATION_SECONDS), inline(0))

        return dsl.select(dowField, sessionCount, totalDuration)
            .from(rs)
            .where(rs.USER_ID.eq(userId))
            .and(if (year != null) year(localTime).eq(year) else noCondition())
            .and(if (month != null) month(localTime).eq(month) else noCondition())
            .groupBy(dowField)
            .orderBy(dowField)
            .fetch { r ->
                FavoriteReadingDay(
                    dayOfWeek = r.get(dowField)!!,
                    sessionCount = r.get(sessionCount)!!.toLong(),
                    totalDurationSeconds = r.get(totalDuration)!!.toLong()
                )
            }
    }

    // ========================================================================
    // Genre statistics
    // ========================================================================

    fun findGenreStatisticsByUser(userId: Long): List<GenreStatistic> =
        findGenreStats(userId, bookTypeFilter = null, orderByDuration = false)

    // ========================================================================
    // Paginated sessions for a book
    // ========================================================================

    fun findByUserIdAndBookId(userId: Long, bookId: Long, pageable: Pageable): Page<ReadingSessionDetail> {
        val condition = rs.USER_ID.eq(userId).and(rs.BOOK_ID.eq(bookId))

        val total = dsl.fetchCount(rs, condition)

        val content = dsl.select(
            rs.ID, BOOK.ID, bm.TITLE, rs.BOOK_TYPE,
            rs.START_TIME, rs.END_TIME, rs.DURATION_SECONDS,
            rs.START_PROGRESS, rs.END_PROGRESS, rs.PROGRESS_DELTA,
            rs.START_LOCATION, rs.END_LOCATION, rs.CREATED_AT
        )
            .from(rs)
            .join(BOOK).on(rs.BOOK_ID.eq(BOOK.ID))
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(condition)
            .orderBy(rs.START_TIME.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { r ->
                ReadingSessionDetail(
                    id = r.get(rs.ID)!!,
                    bookId = r.get(BOOK.ID)!!,
                    bookTitle = r.get(bm.TITLE),
                    bookType = r.get(rs.BOOK_TYPE)!!,
                    startTime = r.get(rs.START_TIME)!!,
                    endTime = r.get(rs.END_TIME)!!,
                    durationSeconds = r.get(rs.DURATION_SECONDS)!!,
                    startProgress = r.get(rs.START_PROGRESS),
                    endProgress = r.get(rs.END_PROGRESS),
                    progressDelta = r.get(rs.PROGRESS_DELTA),
                    startLocation = r.get(rs.START_LOCATION),
                    endLocation = r.get(rs.END_LOCATION),
                    createdAt = r.get(rs.CREATED_AT)!!
                )
            }

        return PageableHelper.toPage(content, total.toLong(), pageable)
    }

    // ========================================================================
    // Page turner sessions (completed books)
    // ========================================================================

    fun findPageTurnerSessionsByUser(userId: Long): List<PageTurnerSession> {
        val dateFinished = coalesce(ubp.DATE_FINISHED, ubp.READ_STATUS_MODIFIED_TIME, ubp.LAST_READ_TIME)
        val bookTitle = coalesce(bm.TITLE, inline("Unknown Book"))

        return dsl.select(
            BOOK.ID,
            bookTitle,
            bm.PAGE_COUNT,
            ubp.PERSONAL_RATING,
            dateFinished,
            rs.START_TIME,
            rs.END_TIME,
            rs.DURATION_SECONDS
        )
            .from(rs)
            .join(BOOK).on(rs.BOOK_ID.eq(BOOK.ID))
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .join(ubp).on(ubp.BOOK_ID.eq(BOOK.ID).and(ubp.USER_ID.eq(rs.USER_ID)))
            .where(rs.USER_ID.eq(userId))
            .and(ubp.READ_STATUS.eq("READ"))
            .and(dateFinished.isNotNull)
            .orderBy(BOOK.ID, rs.START_TIME.asc())
            .fetch { r ->
                PageTurnerSession(
                    bookId = r.get(BOOK.ID)!!,
                    bookTitle = r.get(bookTitle)!!,
                    pageCount = r.get(bm.PAGE_COUNT),
                    personalRating = r.get(ubp.PERSONAL_RATING)?.toInt(),
                    dateFinished = r.get(dateFinished),
                    startTime = r.get(rs.START_TIME)!!,
                    endTime = r.get(rs.END_TIME)!!,
                    durationSeconds = r.get(rs.DURATION_SECONDS)!!
                )
            }
    }

    // ========================================================================
    // Completion race
    // ========================================================================

    fun findCompletionRaceSessionsByUserAndYear(userId: Long, year: Int): List<CompletionRaceSession> {
        val dateFinished = coalesce(ubp.DATE_FINISHED, ubp.READ_STATUS_MODIFIED_TIME, ubp.LAST_READ_TIME)
        val bookTitle = coalesce(bm.TITLE, inline("Unknown Book"))

        return dsl.select(
            BOOK.ID,
            bookTitle,
            rs.START_TIME,
            rs.END_PROGRESS
        )
            .from(rs)
            .join(BOOK).on(rs.BOOK_ID.eq(BOOK.ID))
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .join(ubp).on(ubp.BOOK_ID.eq(BOOK.ID).and(ubp.USER_ID.eq(rs.USER_ID)))
            .where(rs.USER_ID.eq(userId))
            .and(ubp.READ_STATUS.eq("READ"))
            .and(year(dateFinished).eq(year))
            .and(rs.END_PROGRESS.isNotNull)
            .orderBy(BOOK.ID, rs.START_TIME.asc())
            .fetch { r ->
                CompletionRaceSession(
                    bookId = r.get(BOOK.ID)!!,
                    bookTitle = r.get(bookTitle)!!,
                    sessionDate = r.get(rs.START_TIME)!!,
                    endProgress = r.get(rs.END_PROGRESS)
                )
            }
    }

    // ========================================================================
    // Session scatter
    // ========================================================================

    fun findSessionScatterByUserAndYear(userId: Long, year: Int, tzOffset: String): List<SessionScatter> =
        findScatter(userId, tzOffset) { year(it).eq(year) }

    // ========================================================================
    // Listening (audiobook) stats
    // ========================================================================

    fun findListeningSessionsByUserAndMonth(
        userId: Long,
        year: Int,
        month: Int,
        tzOffset: String
    ): List<ListeningHeatmap> {
        val localTime = convertTz(rs.START_TIME, tzOffset)
        val dateField = dateOf(localTime)
        val sessions = count()
        val durationMinutes = coalesce(
            round(sum(rs.DURATION_SECONDS).cast(Double::class.java).div(inline(60.0))),
            inline(0.0)
        )

        return dsl.select(dateField, sessions, durationMinutes)
            .from(rs)
            .where(rs.USER_ID.eq(userId))
            .and(rs.BOOK_TYPE.eq("AUDIOBOOK"))
            .and(year(localTime).eq(year))
            .and(month(localTime).eq(month))
            .groupBy(dateField)
            .orderBy(dateField)
            .fetch { r ->
                ListeningHeatmap(
                    date = r.get(dateField)!!,
                    sessions = r.get(sessions)!!.toLong(),
                    durationMinutes = r.get(durationMinutes)!!.toLong()
                )
            }
    }

    fun findWeeklyListeningTrend(userId: Long, weeks: Int, tzOffset: String): List<WeeklyListeningTrend> {
        val localTime = convertTz(rs.START_TIME, tzOffset)
        val yearField = year(localTime)
        val weekField = function("WEEK", Int::class.java, localTime, inline(3))
        val totalDuration = coalesce(sum(rs.DURATION_SECONDS), inline(0))
        val sessions = count()
        val cutoff = function(
            "DATE_SUB", LocalDateTime::class.java,
            currentLocalDateTime(),
            keyword("INTERVAL").let { field("{0} {1} {2}", it, inline(weeks), keyword("WEEK")) }
        )

        return dsl.select(yearField, weekField, totalDuration, sessions)
            .from(rs)
            .where(rs.USER_ID.eq(userId))
            .and(rs.BOOK_TYPE.eq("AUDIOBOOK"))
            .and(localTime.ge(cutoff))
            .groupBy(yearField, weekField)
            .orderBy(yearField, weekField)
            .fetch { r ->
                WeeklyListeningTrend(
                    year = r.get(yearField)!!,
                    week = r.get(weekField)!!,
                    totalDurationSeconds = r.get(totalDuration)!!.toLong(),
                    sessions = r.get(sessions)!!.toLong()
                )
            }
    }

    fun findAudiobookProgressByUser(userId: Long): List<AudiobookProgress> {
        val title = coalesce(bm.TITLE, inline("Unknown"))
        val endProgress = coalesce(max(rs.END_PROGRESS), inline(0.0))
        val totalDuration = coalesce(max(bf.DURATION_SECONDS), inline(0L))
        val listenedDuration = sum(rs.DURATION_SECONDS)

        return dsl.select(
            rs.BOOK_ID,
            title.`as`("title"),
            endProgress.`as`("max_progress"),
            totalDuration.`as`("total_duration"),
            listenedDuration.`as`("listened_duration")
        )
            .from(rs)
            .join(BOOK).on(rs.BOOK_ID.eq(BOOK.ID))
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .leftJoin(bf).on(bf.BOOK_ID.eq(BOOK.ID).and(bf.BOOK_TYPE.eq("AUDIOBOOK")))
            .where(rs.USER_ID.eq(userId))
            .and(rs.BOOK_TYPE.eq("AUDIOBOOK"))
            .groupBy(rs.BOOK_ID, bm.TITLE)
            .fetch { r ->
                AudiobookProgress(
                    bookId = r.get(rs.BOOK_ID)!!,
                    title = r.get(title)!!,
                    maxProgress = r.get(endProgress),
                    totalDurationSeconds = r.get(totalDuration),
                    listenedDurationSeconds = r.get(listenedDuration)?.toLong()
                )
            }
    }

    fun findMonthlyCompletedAudiobooks(userId: Long): List<MonthlyCompletedAudiobook> {
        val dateFinished = coalesce(ubp.DATE_FINISHED, ubp.READ_STATUS_MODIFIED_TIME)
        val yearField = year(dateFinished)
        val monthField = month(dateFinished)
        val booksCompleted = count()

        return dsl.select(yearField, monthField, booksCompleted)
            .from(ubp)
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.READ_STATUS.eq("READ"))
            .and(dateFinished.isNotNull)
            .and(
                exists(
                    selectOne().from(rs)
                        .where(rs.BOOK_ID.eq(ubp.BOOK_ID))
                        .and(rs.USER_ID.eq(ubp.USER_ID))
                        .and(rs.BOOK_TYPE.eq("AUDIOBOOK"))
                )
            )
            .groupBy(yearField, monthField)
            .orderBy(yearField.desc(), monthField.desc())
            .fetch { r ->
                MonthlyCompletedAudiobook(
                    year = r.get(yearField)!!,
                    month = r.get(monthField)!!,
                    booksCompleted = r.get(booksCompleted)!!.toLong()
                )
            }
    }

    fun findMonthlyListeningDurations(userId: Long, tzOffset: String): List<MonthlyListeningDuration> {
        val localTime = convertTz(rs.START_TIME, tzOffset)
        val yearField = year(localTime)
        val monthField = month(localTime)
        val totalDuration = coalesce(sum(rs.DURATION_SECONDS), inline(0))

        return dsl.select(yearField, monthField, totalDuration)
            .from(rs)
            .where(rs.USER_ID.eq(userId))
            .and(rs.BOOK_TYPE.eq("AUDIOBOOK"))
            .groupBy(yearField, monthField)
            .orderBy(yearField.desc(), monthField.desc())
            .fetch { r ->
                MonthlyListeningDuration(
                    year = r.get(yearField)!!,
                    month = r.get(monthField)!!,
                    totalDurationSeconds = r.get(totalDuration)!!.toLong()
                )
            }
    }

    fun findListeningPeakHoursByUser(userId: Long, year: Int?, month: Int?, tzOffset: String): List<PeakReadingHour> =
        findPeakHours(userId, year, month, tzOffset, bookTypeFilter = "AUDIOBOOK")

    fun findListeningGenreStatisticsByUser(userId: Long): List<GenreStatistic> =
        findGenreStats(userId, bookTypeFilter = "AUDIOBOOK", orderByDuration = true)

    fun findListeningAuthorStatsByUser(userId: Long): List<ListeningAuthor> {
        val bam = BOOK_METADATA_AUTHOR_MAPPING
        val bookCount = countDistinct(rs.BOOK_ID)
        val totalSessions = count()
        val totalDuration = coalesce(sum(rs.DURATION_SECONDS), inline(0))

        return dsl.select(AUTHOR.NAME, bookCount, totalSessions, totalDuration)
            .from(rs)
            .join(bam).on(bam.BOOK_ID.eq(rs.BOOK_ID))
            .join(AUTHOR).on(AUTHOR.ID.eq(bam.AUTHOR_ID))
            .where(rs.USER_ID.eq(userId))
            .and(rs.BOOK_TYPE.eq("AUDIOBOOK"))
            .groupBy(AUTHOR.NAME)
            .orderBy(totalDuration.desc())
            .fetch { r ->
                ListeningAuthor(
                    authorName = r.get(AUTHOR.NAME)!!,
                    bookCount = r.get(bookCount)!!.toLong(),
                    totalSessions = r.get(totalSessions)!!.toLong(),
                    totalDurationSeconds = r.get(totalDuration)!!.toLong()
                )
            }
    }

    fun findListeningSessionScatterByUser(userId: Long, tzOffset: String): List<SessionScatter> =
        findScatter(userId, tzOffset) { _ -> rs.BOOK_TYPE.eq("AUDIOBOOK") }

    fun findBookTimelineByUserAndYear(userId: Long, year: Int, tzOffset: String): List<BookTimeline> {
        val localTime = convertTz(rs.START_TIME, tzOffset)
        val bookTitle = coalesce(bm.TITLE, inline("Unknown"))
        val firstSession = min(convertTz(rs.START_TIME, tzOffset))
        val lastSession = max(convertTz(rs.END_TIME, tzOffset))
        val totalSessions = count()
        val totalDuration = coalesce(sum(rs.DURATION_SECONDS), inline(0))
        val maxProgress = coalesce(max(rs.END_PROGRESS), inline(0.0)).div(inline(100.0))

        return dsl.select(
            rs.BOOK_ID,
            bookTitle,
            bm.PAGE_COUNT,
            firstSession,
            lastSession,
            totalSessions,
            totalDuration,
            maxProgress,
            ubp.READ_STATUS
        )
            .from(rs)
            .join(BOOK).on(rs.BOOK_ID.eq(BOOK.ID))
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .leftJoin(ubp).on(ubp.BOOK_ID.eq(rs.BOOK_ID).and(ubp.USER_ID.eq(rs.USER_ID)))
            .where(rs.USER_ID.eq(userId))
            .and(year(localTime).eq(year))
            .groupBy(rs.BOOK_ID, bm.TITLE, bm.PAGE_COUNT, ubp.READ_STATUS)
            .orderBy(firstSession)
            .fetch { r ->
                BookTimeline(
                    bookId = r.get(rs.BOOK_ID)!!,
                    title = r.get(bookTitle)!!,
                    pageCount = r.get(bm.PAGE_COUNT),
                    firstSessionDate = r.get(firstSession),
                    lastSessionDate = r.get(lastSession),
                    totalSessions = r.get(totalSessions)!!,
                    totalDurationSeconds = r.get(totalDuration)!!.toLong(),
                    maxProgress = r.get(maxProgress),
                    readStatus = r.get(ubp.READ_STATUS)
                )
            }
    }

    // ========================================================================
    // Shared query builders (reduce duplication between reading/listening)
    // ========================================================================

    private fun findPeakHours(
        userId: Long, year: Int?, month: Int?, tzOffset: String, bookTypeFilter: String?
    ): List<PeakReadingHour> {
        val localTime = convertTz(rs.START_TIME, tzOffset)
        val hourField = hour(localTime)
        val sessionCount = count()
        val totalDuration = sum(rs.DURATION_SECONDS)

        return dsl.select(hourField, sessionCount, totalDuration)
            .from(rs)
            .where(rs.USER_ID.eq(userId))
            .and(if (bookTypeFilter != null) rs.BOOK_TYPE.eq(bookTypeFilter) else noCondition())
            .and(if (year != null) year(localTime).eq(year) else noCondition())
            .and(if (month != null) month(localTime).eq(month) else noCondition())
            .groupBy(hourField)
            .orderBy(hourField)
            .fetch { r ->
                PeakReadingHour(
                    hourOfDay = r.get(hourField)!!,
                    sessionCount = r.get(sessionCount)!!.toLong(),
                    totalDurationSeconds = r.get(totalDuration)!!.toLong()
                )
            }
    }

    private fun findGenreStats(userId: Long, bookTypeFilter: String?, orderByDuration: Boolean): List<GenreStatistic> {
        val bmc = BOOK_METADATA_CATEGORY_MAPPING
        val bookCount = countDistinct(BOOK.ID)
        val totalSessions = count()
        val totalDuration = sum(rs.DURATION_SECONDS)

        return dsl.select(CATEGORY.NAME, bookCount, totalSessions, totalDuration)
            .from(rs)
            .join(BOOK).on(rs.BOOK_ID.eq(BOOK.ID))
            .join(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .join(bmc).on(bmc.BOOK_ID.eq(bm.BOOK_ID))
            .join(CATEGORY).on(CATEGORY.ID.eq(bmc.CATEGORY_ID))
            .where(rs.USER_ID.eq(userId))
            .and(if (bookTypeFilter != null) rs.BOOK_TYPE.eq(bookTypeFilter) else noCondition())
            .groupBy(CATEGORY.NAME)
            .orderBy(if (orderByDuration) totalDuration.desc() else totalSessions.desc())
            .fetch { r ->
                GenreStatistic(
                    genre = r.get(CATEGORY.NAME)!!,
                    bookCount = r.get(bookCount)!!.toLong(),
                    totalSessions = r.get(totalSessions)!!.toLong(),
                    totalDurationSeconds = r.get(totalDuration)!!.toLong()
                )
            }
    }

    private fun findScatter(
        userId: Long,
        tzOffset: String,
        extraCondition: ((Field<LocalDateTime>) -> org.jooq.Condition)? = null
    ): List<SessionScatter> {
        val localTime = convertTz(rs.START_TIME, tzOffset)
        val hourOfDay = hour(localTime).cast(Double::class.java).plus(
            minute(localTime).cast(Double::class.java).div(inline(60.0))
        )
        val durationMinutes = rs.DURATION_SECONDS.cast(Double::class.java).div(inline(60.0))
        val dow = dayOfWeek(localTime)

        return dsl.select(hourOfDay, durationMinutes, dow)
            .from(rs)
            .where(rs.USER_ID.eq(userId))
            .and(extraCondition?.invoke(localTime) ?: noCondition())
            .orderBy(rs.START_TIME.desc())
            .limit(500)
            .fetch { r ->
                SessionScatter(
                    hourOfDay = r.get(hourOfDay)!!,
                    durationMinutes = r.get(durationMinutes)!!,
                    dayOfWeek = r.get(dow)!!
                )
            }
    }
}
