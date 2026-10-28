package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class CbxPageInfo constructor(
        val pageNumber: Int? = null,
        val displayName: String? = null
)
