package org.booklore.repository.jooq.dto

import org.booklore.model.enums.IconType
import java.time.LocalDateTime

data class MagicShelfRow(
    val id: Long,
    val userId: Long,
    val name: String,
    val icon: String?,
    val iconType: IconType?,
    val filterJson: String,
    val isPublic: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
