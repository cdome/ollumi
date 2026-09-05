package org.booklore.repository.jooq.dto

import java.time.Instant

/** Read view of an `oidc_session` row (replaces OidcSessionEntity for the OIDC logout flows). */
data class OidcSession(
    val id: Long,
    val userId: Long,
    val oidcSubject: String,
    val oidcIssuer: String,
    val oidcSessionId: String?,
    val idTokenHint: String?,
    val createdAt: Instant,
    val lastRefreshedAt: Instant?,
    val revoked: Boolean,
)
