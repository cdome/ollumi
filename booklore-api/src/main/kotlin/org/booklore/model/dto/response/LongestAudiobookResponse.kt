package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class LongestAudiobookResponse constructor(
        val bookId: Long? = null,
        val title: String? = null,
        val totalDurationSeconds: Long? = null,
        val listenedDurationSeconds: Long? = null,
        val progressPercent: Double? = null
)
