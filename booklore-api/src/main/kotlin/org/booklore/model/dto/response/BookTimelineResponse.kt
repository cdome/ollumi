package org.booklore.model.dto.response

import java.time.LocalDate
import com.fasterxml.jackson.annotation.JsonCreator

data class BookTimelineResponse constructor(
        val bookId: Long? = null,
        val title: String? = null,
        val pageCount: Int? = null,
        val firstSessionDate: LocalDate? = null,
        val lastSessionDate: LocalDate? = null,
        val totalSessions: Int? = null,
        val totalDurationSeconds: Long? = null,
        val maxProgress: Double? = null,
        val readStatus: String? = null
)
