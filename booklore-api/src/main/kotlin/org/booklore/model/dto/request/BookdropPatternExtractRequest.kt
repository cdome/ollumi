package org.booklore.model.dto.request

import jakarta.validation.constraints.NotBlank

data class BookdropPatternExtractRequest(
    @field:NotBlank
    val pattern: String? = null,
    val selectAll: Boolean? = null,
    val excludedIds: List<Long>? = null,
    val selectedIds: List<Long>? = null,
    val preview: Boolean? = null
)
