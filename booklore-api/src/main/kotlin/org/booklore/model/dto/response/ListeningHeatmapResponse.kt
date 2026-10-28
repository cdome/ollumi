package org.booklore.model.dto.response

import java.time.LocalDate
import com.fasterxml.jackson.annotation.JsonCreator

data class ListeningHeatmapResponse constructor(
        val date: LocalDate? = null,
        val sessions: Long? = null,
        val durationMinutes: Long? = null
)
