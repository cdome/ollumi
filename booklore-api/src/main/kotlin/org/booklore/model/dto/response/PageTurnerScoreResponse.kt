package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class PageTurnerScoreResponse constructor(
        val bookId: Long? = null,
        val bookTitle: String? = null,
        val categories: List<String?>? = null,
        val pageCount: Int? = null,
        val personalRating: Int? = null,
        val gripScore: Int? = null,
        val totalSessions: Long? = null,
        val avgSessionDurationSeconds: Double? = null,
        val sessionAcceleration: Double? = null,
        val gapReduction: Double? = null,
        val finishBurst: Boolean? = null
)
