package org.booklore.repository.jooq.dto

import org.booklore.model.enums.BookdropFileStatus
import java.time.Instant

data class BookdropFileRow(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val fileSize: Long?,
    val status: BookdropFileStatus,
    val originalMetadata: String?,
    val fetchedMetadata: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)
