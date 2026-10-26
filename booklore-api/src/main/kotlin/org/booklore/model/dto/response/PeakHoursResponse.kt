package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class PeakHoursResponse constructor(
        val hourOfDay: Int? = null,
        val sessionCount: Long? = null,
        val totalDurationSeconds: Long? = null
)
