package org.booklore.repository.jooq

import org.booklore.jooq.tables.OidcSession.OIDC_SESSION
import org.booklore.jooq.tables.records.OidcSessionRecord
import org.booklore.repository.jooq.dto.OidcSession
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class JooqOidcSessionRepository(private val dsl: DSLContext) {

    private val t = OIDC_SESSION

    fun findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId: Long): OidcSession? =
        dsl.selectFrom(t)
            .where(t.USER_ID.eq(userId).and(t.REVOKED.eq(0.toByte())))
            .orderBy(t.CREATED_AT.desc())
            .limit(1)
            .fetchOne()?.let(::toDto)

    fun findByOidcSessionIdAndRevokedFalse(oidcSessionId: String): List<OidcSession> =
        dsl.selectFrom(t)
            .where(t.OIDC_SESSION_ID.eq(oidcSessionId).and(t.REVOKED.eq(0.toByte())))
            .fetch().map(::toDto)

    fun findByOidcSubjectAndOidcIssuerAndRevokedFalse(oidcSubject: String, oidcIssuer: String): List<OidcSession> =
        dsl.selectFrom(t)
            .where(t.OIDC_SUBJECT.eq(oidcSubject).and(t.OIDC_ISSUER.eq(oidcIssuer)).and(t.REVOKED.eq(0.toByte())))
            .fetch().map(::toDto)

    fun insert(userId: Long, oidcSubject: String, oidcIssuer: String, oidcSessionId: String?, idTokenHint: String?) {
        dsl.insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.OIDC_SUBJECT, oidcSubject)
            .set(t.OIDC_ISSUER, oidcIssuer)
            .set(t.OIDC_SESSION_ID, oidcSessionId)
            .set(t.ID_TOKEN_HINT, idTokenHint)
            .set(t.CREATED_AT, LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC))
            .set(t.REVOKED, 0.toByte())
            .execute()
    }

    fun revokeById(id: Long) {
        dsl.update(t).set(t.REVOKED, 1.toByte()).where(t.ID.eq(id)).execute()
    }

    fun deleteRevokedCreatedBefore(cutoff: Instant) {
        dsl.deleteFrom(t)
            .where(t.REVOKED.eq(1.toByte()).and(t.CREATED_AT.lt(cutoff.toLocalDateTimeUtc())))
            .execute()
    }

    fun deleteCreatedBefore(cutoff: Instant) {
        dsl.deleteFrom(t)
            .where(t.CREATED_AT.lt(cutoff.toLocalDateTimeUtc()))
            .execute()
    }

    private fun toDto(r: OidcSessionRecord): OidcSession =
        OidcSession(
            id = r.id,
            userId = r.userId,
            oidcSubject = r.oidcSubject,
            oidcIssuer = r.oidcIssuer,
            oidcSessionId = r.get(t.OIDC_SESSION_ID),
            idTokenHint = r.get(t.ID_TOKEN_HINT),
            createdAt = r.get(t.CREATED_AT).toInstantUtc(),
            lastRefreshedAt = r.get(t.LAST_REFRESHED_AT)?.toInstantUtc(),
            revoked = (r.get(t.REVOKED) ?: 0).toInt() != 0,
        )

    private fun Instant.toLocalDateTimeUtc(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
    private fun LocalDateTime.toInstantUtc(): Instant = this.toInstant(ZoneOffset.UTC)
}
