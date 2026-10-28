package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class SessionScatterResponse constructor(
        val hourOfDay: Double? = null,
        val durationMinutes: Double? = null,
        val dayOfWeek: Int? = null
)
