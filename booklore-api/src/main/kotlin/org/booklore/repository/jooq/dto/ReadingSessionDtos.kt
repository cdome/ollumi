package org.booklore.repository.jooq.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class ReadingSessionCount(val date: LocalDate, val count: Long)

data class ReadingSessionTimeline(
    val bookId: Long,
    val bookTitle: String,
    val bookFileType: String,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val totalSessions: Long,
    val totalDurationSeconds: Long
)

data class ReadingSpeed(val date: LocalDate, val avgProgressPerMinute: Double, val totalSessions: Int)

data class PeakReadingHour(val hourOfDay: Int, val sessionCount: Long, val totalDurationSeconds: Long)

data class FavoriteReadingDay(val dayOfWeek: Int, val sessionCount: Long, val totalDurationSeconds: Long)

data class GenreStatistic(
    val genre: String,
    val bookCount: Long,
    val totalSessions: Long,
    val totalDurationSeconds: Long
)

data class PageTurnerSession(
    val bookId: Long,
    val bookTitle: String,
    val pageCount: Int?,
    val personalRating: Int?,
    val dateFinished: LocalDateTime?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val durationSeconds: Int
)

data class CompletionRaceSession(
    val bookId: Long,
    val bookTitle: String,
    val sessionDate: LocalDateTime,
    val endProgress: Double?
)

data class SessionScatter(val hourOfDay: Double, val durationMinutes: Double, val dayOfWeek: Int)

data class ListeningHeatmap(val date: LocalDate, val sessions: Long, val durationMinutes: Long)

data class WeeklyListeningTrend(val year: Int, val week: Int, val totalDurationSeconds: Long, val sessions: Long)

data class AudiobookProgress(
    val bookId: Long,
    val title: String,
    val maxProgress: Double?,
    val totalDurationSeconds: Long?,
    val listenedDurationSeconds: Long?
)

data class MonthlyCompletedAudiobook(val year: Int, val month: Int, val booksCompleted: Long)

data class MonthlyListeningDuration(val year: Int, val month: Int, val totalDurationSeconds: Long)

data class ListeningAuthor(
    val authorName: String,
    val bookCount: Long,
    val totalSessions: Long,
    val totalDurationSeconds: Long
)

data class BookTimeline(
    val bookId: Long,
    val title: String,
    val pageCount: Int?,
    val firstSessionDate: LocalDateTime?,
    val lastSessionDate: LocalDateTime?,
    val totalSessions: Int,
    val totalDurationSeconds: Long,
    val maxProgress: Double?,
    val readStatus: String?
)

data class ReadingSessionDetail(
    val id: Long,
    val bookId: Long,
    val bookTitle: String?,
    val bookType: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val durationSeconds: Int,
    val startProgress: Double?,
    val endProgress: Double?,
    val progressDelta: Double?,
    val startLocation: String?,
    val endLocation: String?,
    val createdAt: LocalDateTime
)
