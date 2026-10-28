package org.booklore.model.dto.request

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class AttachBookFileRequest(
    @field:NotNull
    @field:NotEmpty
    val sourceBookIds: List<Long>? = null,

    @get:JvmName("isMoveFiles")
    val moveFiles: Boolean = false
)
