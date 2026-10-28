package org.booklore.model.dto.response

import java.time.Instant
import com.fasterxml.jackson.annotation.JsonCreator

data class CompletionRaceResponse constructor(
        val bookId: Long? = null,
        val bookTitle: String? = null,
        val sessionDate: Instant? = null,
        val endProgress: Float? = null
)
