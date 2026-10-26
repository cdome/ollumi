package org.booklore.model.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

data class SvgIconBatchRequest(
    @field:NotEmpty(message = "Icons list cannot be empty")
    @field:Valid
    val icons: List<SvgIconCreateRequest>? = null
)
