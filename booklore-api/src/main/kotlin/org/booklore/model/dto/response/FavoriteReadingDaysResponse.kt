package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class FavoriteReadingDaysResponse constructor(
        val dayOfWeek: Int? = null,
        val dayName: String? = null,
        val sessionCount: Long? = null,
        val totalDurationSeconds: Long? = null
)
