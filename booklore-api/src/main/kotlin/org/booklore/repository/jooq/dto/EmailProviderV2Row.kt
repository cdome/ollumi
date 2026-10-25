package org.booklore.repository.jooq.dto

/**
 * Full `email_provider_v2` row including the password (the web DTO org.booklore.model.dto.EmailProviderV2
 * deliberately omits it). SendEmailV2Service needs the password to build the mail sender.
 */
data class EmailProviderV2Row(
    val id: Long,
    val userId: Long,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val fromAddress: String?,
    val auth: Boolean,
    val startTls: Boolean,
    val defaultProvider: Boolean,
    val shared: Boolean,
)
