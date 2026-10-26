package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class BookdropBulkEditResult constructor(
        val totalFiles: Int? = null,
        val successfullyUpdated: Int? = null,
        val failed: Int? = null
)
