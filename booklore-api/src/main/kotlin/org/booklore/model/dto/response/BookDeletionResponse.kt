package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class BookDeletionResponse constructor(
        val deleted: Set<Long?>? = null,
        val failedFileDeletions: List<Long?>? = null
)
