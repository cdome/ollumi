package org.booklore.model.dto.response

import java.time.LocalDate
import com.fasterxml.jackson.annotation.JsonCreator

data class ReadingSessionHeatmapResponse constructor(
        val date: LocalDate? = null,
        val count: Long? = null
)
