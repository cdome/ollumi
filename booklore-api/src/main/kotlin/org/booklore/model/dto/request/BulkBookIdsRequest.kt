package org.booklore.model.dto.request

import jakarta.validation.constraints.NotEmpty

data class BulkBookIdsRequest(
    @field:NotEmpty(message = "At least one book ID is required")
    val bookIds: Set<Long>? = null
)
