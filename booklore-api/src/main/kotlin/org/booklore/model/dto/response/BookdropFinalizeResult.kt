package org.booklore.model.dto.response

import java.time.Instant
import com.fasterxml.jackson.annotation.JsonCreator

data class BookdropFinalizeResult constructor(
        val totalFiles: Int? = null,
        val successfullyImported: Int? = null,
        val failed: Int? = null,
        val processedAt: Instant? = null,
        val results: List<BookdropFileResult?> = ArrayList()
)
