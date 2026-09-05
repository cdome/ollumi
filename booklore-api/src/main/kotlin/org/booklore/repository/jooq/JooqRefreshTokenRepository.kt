package org.booklore.repository.jooq

import org.booklore.jooq.tables.RefreshToken.REFRESH_TOKEN
import org.booklore.jooq.tables.records.RefreshTokenRecord
import org.booklore.repository.jooq.dto.RefreshToken
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class JooqRefreshTokenRepository(private val dsl: DSLContext) {

    private val t = REFRESH_TOKEN

    fun findByToken(token: String): RefreshToken? =
        dsl.selectFrom(t).where(t.TOKEN.eq(token)).fetchOne()?.let(::toDto)

    fun insert(userId: Long, token: String, expiryDate: Instant, revoked: Boolean) {
        dsl.insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.TOKEN, token)
            .set(t.EXPIRY_DATE, expiryDate.toLocalDateTimeUtc())
            .set(t.REVOKED, revoked.toByteFlag())
            .execute()
    }

    fun revokeById(id: Long, revocationDate: Instant) {
        dsl.update(t)
            .set(t.REVOKED, 1.toByte())
            .set(t.REVOCATION_DATE, revocationDate.toLocalDateTimeUtc())
            .where(t.ID.eq(id))
            .execute()
    }

    /** Bulk-revoke every currently-active token for the user (replaces find-all + per-row save). */
    fun revokeAllActiveByUserId(userId: Long, revocationDate: Instant) {
        dsl.update(t)
            .set(t.REVOKED, 1.toByte())
            .set(t.REVOCATION_DATE, revocationDate.toLocalDateTimeUtc())
            .where(t.USER_ID.eq(userId).and(t.REVOKED.eq(0.toByte())))
            .execute()
    }

    private fun toDto(r: RefreshTokenRecord): RefreshToken =
        RefreshToken(
            id = r.id,
            userId = r.get(t.USER_ID),
            token = r.token,
            expiryDate = r.expiryDate.toInstantUtc(),
            revoked = r.revoked.toInt() != 0,
            revocationDate = r.get(t.REVOCATION_DATE)?.toInstantUtc(),
        )

    private fun Boolean.toByteFlag(): Byte = if (this) 1 else 0
    private fun Instant.toLocalDateTimeUtc(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
    private fun LocalDateTime.toInstantUtc(): Instant = this.toInstant(ZoneOffset.UTC)
}
