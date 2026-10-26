package org.booklore.model.dto.request

import org.booklore.model.dto.BookMetadata

data class BookdropFinalizeRequest(
    val selectAll: Boolean? = null,
    val excludedIds: List<Long>? = null,
    val files: List<BookdropFinalizeFile>? = null,
    val defaultLibraryId: Long? = null,
    val defaultPathId: Long? = null
) {
    data class BookdropFinalizeFile(
        val fileId: Long? = null,
        val libraryId: Long? = null,
        val pathId: Long? = null,
        val metadata: BookMetadata? = null
    )
}
