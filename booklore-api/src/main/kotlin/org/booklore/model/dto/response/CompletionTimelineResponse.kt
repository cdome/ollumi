package org.booklore.model.dto.response

import org.booklore.model.enums.ReadStatus
import com.fasterxml.jackson.annotation.JsonCreator

data class CompletionTimelineResponse constructor(
        val year: Int? = null,
        val month: Int? = null,
        val totalBooks: Long? = null,
        val statusBreakdown: Map<ReadStatus?, Long?>? = null,
        val finishedBooks: Long? = null,
        val completionRate: Double? = null
)
