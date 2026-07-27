package org.booklore.repository.jooq.dto

import java.time.Instant

/** Read view of a `refresh_token` row (replaces RefreshTokenEntity for the auth flows). */
data class RefreshToken(
    val id: Long,
    val userId: Long?,
    val token: String,
    val expiryDate: Instant,
    val revoked: Boolean,
    val revocationDate: Instant?,
)
