package org.booklore.model.dto.response

import java.time.LocalDate
import com.fasterxml.jackson.annotation.JsonCreator

data class ReadingSpeedResponse constructor(
        val date: LocalDate? = null,
        val avgProgressPerMinute: Double? = null,
        val totalSessions: Int? = null
)
