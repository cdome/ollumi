package org.booklore.model.dto.request

data class BookdropSelectionRequest(
    @get:JvmName("isSelectAll")
    val selectAll: Boolean = false,
    val excludedIds: List<Long>? = null,
    val selectedIds: List<Long>? = null
)
