package org.booklore.model.dto.response

import org.booklore.model.enums.AuditAction
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonCreator

data class AuditLogDto constructor(
        val id: Long? = null,
        val userId: Long? = null,
        val username: String? = null,
        val action: AuditAction? = null,
        val entityType: String? = null,
        val entityId: Long? = null,
        val description: String? = null,
        val ipAddress: String? = null,
        val countryCode: String? = null,
        val createdAt: LocalDateTime? = null
)
