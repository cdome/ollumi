package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class WeeklyListeningTrendResponse constructor(
        val year: Int? = null,
        val week: Int? = null,
        val totalDurationSeconds: Long? = null,
        val sessions: Long? = null
)
