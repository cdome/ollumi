package org.booklore.model.dto.request

data class ToggleFieldLocksRequest(
    val bookIds: List<Long>? = null,
    val fieldActions: Map<String, String>? = null
)
