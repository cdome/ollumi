package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class BookCompletionHeatmapResponse constructor(
        val year: Int? = null,
        val month: Int? = null,
        val count: Long? = null
)
