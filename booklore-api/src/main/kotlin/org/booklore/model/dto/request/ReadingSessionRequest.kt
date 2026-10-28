package org.booklore.model.dto.request

import jakarta.validation.constraints.NotNull
import org.booklore.model.enums.BookFileType
import java.time.Instant

data class ReadingSessionRequest(
    @field:NotNull
    val bookId: Long? = null,

    val bookType: BookFileType? = null,

    @field:NotNull
    val startTime: Instant? = null,

    @field:NotNull
    val endTime: Instant? = null,

    @field:NotNull
    val durationSeconds: Int? = null,

    val durationFormatted: String? = null,
    val startProgress: Float? = null,
    val endProgress: Float? = null,
    val progressDelta: Float? = null,
    val startLocation: String? = null,
    val endLocation: String? = null
)
