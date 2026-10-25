package org.booklore.repository.jooq.dto

import java.time.LocalDateTime

data class NotebookEntryRow(
    val id: Long,
    val type: String,
    val bookId: Long,
    val bookTitle: String?,
    val text: String?,
    val note: String?,
    val color: String?,
    val style: String?,
    val chapterTitle: String?,
    val primaryBookType: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
)

data class NotebookBook(
    val bookId: Long,
    val bookTitle: String?
)

data class NotebookBookWithCount(
    val bookId: Long,
    val bookTitle: String?,
    val noteCount: Int,
    val coverUpdatedOn: LocalDateTime?
)
