package org.booklore.model.dto.response

import org.booklore.model.dto.BookMetadata
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class BookdropPatternExtractResult constructor(
        val totalFiles: Int? = null,
        val successfullyExtracted: Int? = null,
        val failed: Int? = null,
        val results: List<FileExtractionResult?>? = null
) {
    data class FileExtractionResult constructor(
            val fileId: Long? = null,
            val fileName: String? = null,
            @get:JvmName("isSuccess")
            @get:JsonProperty("success")
            val success: Boolean? = null,
            val extractedMetadata: BookMetadata? = null,
            val errorMessage: String? = null
    )
}
