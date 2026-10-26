package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class GenreStatisticsResponse constructor(
        val genre: String? = null,
        val bookCount: Long? = null,
        val totalSessions: Long? = null,
        val totalDurationSeconds: Long? = null,
        val averageSessionsPerBook: Double? = null
)
