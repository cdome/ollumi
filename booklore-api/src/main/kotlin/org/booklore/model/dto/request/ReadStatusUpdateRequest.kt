package org.booklore.model.dto.request

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class ReadStatusUpdateRequest(
    @field:NotEmpty(message = "Book IDs cannot be empty")
    val bookIds: List<Long>? = null,

    @field:NotNull(message = "Status cannot be null")
    val status: String? = null
)
