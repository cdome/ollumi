package org.booklore.repository.jooq.dto

import org.booklore.model.enums.FontFormat
import java.time.LocalDateTime

/** Domain view of a `custom_font` row (replaces the former CustomFontEntity). */
data class CustomFont(
    val id: Long,
    val userId: Long,
    val fontName: String,
    val fileName: String,
    val originalFileName: String,
    val format: FontFormat,
    val fileSize: Long,
    val uploadedAt: LocalDateTime,
)
