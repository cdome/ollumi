package org.booklore.model.dto.request

import jakarta.validation.constraints.NotNull
import org.booklore.model.dto.BookMetadata

data class BookdropBulkEditRequest(
    @field:NotNull
    val fields: BookMetadata? = null,

    @field:NotNull
    val enabledFields: Set<String>? = null,

    @get:JvmName("isMergeArrays")
    val mergeArrays: Boolean = false,

    @get:JvmName("isSelectAll")
    val selectAll: Boolean = false,

    val excludedIds: List<Long>? = null,
    val selectedIds: List<Long>? = null
)
