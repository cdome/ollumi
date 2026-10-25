package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.booklore.jooq.tables.RefreshToken.REFRESH_TOKEN
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

class JooqRefreshTokenRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqRefreshTokenRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var otherUserId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(REFRESH_TOKEN).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
        otherUserId = insertUser("other")
    }

    @Test
    fun `insert then findByToken round-trips fields including instant`() {
        val expiry = Instant.now().plus(7, ChronoUnit.DAYS)
        repository.insert(userId, "tok-1", expiry, false)

        val found = repository.findByToken("tok-1")!!
        assertThat(found.userId).isEqualTo(userId)
        assertThat(found.token).isEqualTo("tok-1")
        assertThat(found.revoked).isFalse()
        assertThat(found.revocationDate).isNull()
        assertThat(found.expiryDate).isCloseTo(expiry, within(1, ChronoUnit.SECONDS))
    }

    @Test
    fun `findByToken returns null when absent`() {
        assertThat(repository.findByToken("missing")).isNull()
    }

    @Test
    fun `revokeById marks the single token revoked with a revocation date`() {
        repository.insert(userId, "tok-a", Instant.now().plusSeconds(3600), false)
        val id = repository.findByToken("tok-a")!!.id
        val when1 = Instant.now()

        repository.revokeById(id, when1)

        val revoked = repository.findByToken("tok-a")!!
        assertThat(revoked.revoked).isTrue()
        assertThat(revoked.revocationDate).isCloseTo(when1, within(1, ChronoUnit.SECONDS))
    }

    @Test
    fun `revokeAllActiveByUserId revokes only the user's active tokens`() {
        repository.insert(userId, "active-1", Instant.now().plusSeconds(3600), false)
        repository.insert(userId, "active-2", Instant.now().plusSeconds(3600), false)
        repository.insert(userId, "already-revoked", Instant.now().plusSeconds(3600), true)
        repository.insert(otherUserId, "other-active", Instant.now().plusSeconds(3600), false)

        repository.revokeAllActiveByUserId(userId, Instant.now())

        assertThat(repository.findByToken("active-1")!!.revoked).isTrue()
        assertThat(repository.findByToken("active-2")!!.revoked).isTrue()
        // the other user's active token is untouched
        assertThat(repository.findByToken("other-active")!!.revoked).isFalse()
    }

    @Test
    fun `revokeAllActiveByUserId leaves an already-revoked token's original date`() {
        repository.insert(userId, "tok", Instant.now().plusSeconds(3600), false)
        val firstRevoke = Instant.now().minus(1, ChronoUnit.HOURS)
        val id = repository.findByToken("tok")!!.id
        repository.revokeById(id, firstRevoke)

        repository.revokeAllActiveByUserId(userId, Instant.now())

        // second call only targets revoked=0 rows, so the original revocation date stands
        assertThat(repository.findByToken("tok")!!.revocationDate).isCloseTo(firstRevoke, within(1, ChronoUnit.SECONDS))
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
