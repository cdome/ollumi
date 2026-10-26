package org.booklore.model.dto.response

import org.booklore.model.enums.BookFileType
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonCreator

data class ReadingSessionTimelineResponse constructor(
        val bookId: Long? = null,
        val bookTitle: String? = null,
        val bookType: BookFileType? = null,
        val startDate: LocalDateTime? = null,
        val endDate: LocalDateTime? = null,
        val totalSessions: Long? = null,
        val totalDurationSeconds: Long? = null
)
