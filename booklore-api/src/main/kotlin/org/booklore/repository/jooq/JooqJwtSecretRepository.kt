package org.booklore.repository.jooq

import org.booklore.jooq.tables.JwtSecret.JWT_SECRET
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqJwtSecretRepository(private val dsl: DSLContext) {

    /** The most recently created JWT secret, or null if none has been stored yet. */
    fun findLatestSecret(): String? =
        dsl.select(JWT_SECRET.SECRET)
            .from(JWT_SECRET)
            .orderBy(JWT_SECRET.CREATED_AT.desc())
            .limit(1)
            .fetchOne(JWT_SECRET.SECRET)

    fun insertSecret(secret: String) {
        dsl.insertInto(JWT_SECRET)
            .set(JWT_SECRET.SECRET, secret)
            .set(JWT_SECRET.CREATED_AT, LocalDateTime.now())
            .execute()
    }
}
