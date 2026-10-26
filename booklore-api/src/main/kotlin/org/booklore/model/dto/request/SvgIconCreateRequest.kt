package org.booklore.model.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SvgIconCreateRequest(
    @field:NotBlank(message = "SVG name is required")
    @field:Size(min = 1, max = 255, message = "SVG name must be between 1 and 255 characters")
    @field:Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "SVG name can only contain alphanumeric characters and hyphens")
    val svgName: String? = null,

    @field:NotBlank(message = "SVG data is required")
    @field:Size(max = 1048576, message = "SVG data must not exceed 1MB")
    val svgData: String? = null
)
