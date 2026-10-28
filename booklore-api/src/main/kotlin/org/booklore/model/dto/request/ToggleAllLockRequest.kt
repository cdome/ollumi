package org.booklore.model.dto.request

import org.booklore.model.enums.Lock

data class ToggleAllLockRequest(
    val bookIds: Set<Long>? = null,
    val lock: Lock? = null
)
