package org.booklore.model.dto.response

import org.booklore.model.enums.BookFileType
import java.time.Instant
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonCreator

data class ReadingSessionResponse constructor(
        val id: Long? = null,
        val bookId: Long? = null,
        val bookTitle: String? = null,
        val bookType: BookFileType? = null,
        val startTime: Instant? = null,
        val endTime: Instant? = null,
        val durationSeconds: Int? = null,
        val startProgress: Float? = null,
        val endProgress: Float? = null,
        val progressDelta: Float? = null,
        val startLocation: String? = null,
        val endLocation: String? = null,
        val createdAt: LocalDateTime? = null
)
