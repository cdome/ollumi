package org.booklore.model.dto.request

data class ShelvesAssignmentRequest(
    val bookIds: Set<Long>? = null,
    val shelvesToAssign: Set<Long>? = null,
    val shelvesToUnassign: Set<Long>? = null
)
