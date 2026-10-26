package org.booklore.model.dto.request

import org.booklore.model.enums.OpdsSortOrder

data class OpdsUserV2CreateRequest(
    val username: String? = null,
    val password: String? = null,
    val sortOrder: OpdsSortOrder? = null
)
