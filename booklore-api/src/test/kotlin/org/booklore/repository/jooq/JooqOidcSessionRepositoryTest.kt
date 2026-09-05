package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.booklore.jooq.tables.OidcSession.OIDC_SESSION
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class JooqOidcSessionRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqOidcSessionRepository
    @Autowired private lateinit var dsl: DSLContext

    private val issuer = "https://issuer.example.com"

    private var userId: Long = 0
    private var otherUserId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(OIDC_SESSION).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
        otherUserId = insertUser("other")
    }

    @Test
    fun `insert round-trips fields, defaults revoked false and sets created_at`() {
        repository.insert(userId, "sub-1", issuer, "sess-1", "id-token-1")

        val found = repository.findByOidcSessionIdAndRevokedFalse("sess-1")
        assertThat(found).hasSize(1)
        val s = found[0]
        assertThat(s.userId).isEqualTo(userId)
        assertThat(s.oidcSubject).isEqualTo("sub-1")
        assertThat(s.oidcIssuer).isEqualTo(issuer)
        assertThat(s.oidcSessionId).isEqualTo("sess-1")
        assertThat(s.idTokenHint).isEqualTo("id-token-1")
        assertThat(s.revoked).isFalse()
        assertThat(s.createdAt).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES))
        assertThat(s.lastRefreshedAt).isNull()
    }

    @Test
    fun `insert handles null oidc_session_id and id_token_hint`() {
        repository.insert(userId, "sub-null", issuer, null, null)

        val found = repository.findByOidcSubjectAndOidcIssuerAndRevokedFalse("sub-null", issuer)
        assertThat(found).hasSize(1)
        assertThat(found[0].oidcSessionId).isNull()
        assertThat(found[0].idTokenHint).isNull()
    }

    @Test
    fun `findFirst returns newest non-revoked session, ignoring revoked and other users`() {
        insertSession(subject = "s", idTokenHint = "old", createdAt = daysAgo(2))
        val newest = insertSession(subject = "s", idTokenHint = "new", createdAt = hoursAgo(1))
        // A revoked session that is even newer must be ignored.
        insertSession(subject = "s", idTokenHint = "revoked-newest", createdAt = nowUtc(), revoked = 1)
        // A newer session belonging to another user must be ignored.
        insertSession(userId = otherUserId, subject = "s", createdAt = nowUtc())

        val result = repository.findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(newest)
        assertThat(result.idTokenHint).isEqualTo("new")
    }

    @Test
    fun `findFirst returns null when the user has no non-revoked sessions`() {
        insertSession(revoked = 1)

        assertThat(repository.findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId)).isNull()
        // And null for a user with no sessions at all.
        assertThat(repository.findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(otherUserId)).isNull()
    }

    @Test
    fun `findByOidcSessionIdAndRevokedFalse returns only non-revoked matches`() {
        insertSession(sessionId = "sid-x")
        insertSession(userId = otherUserId, sessionId = "sid-x")
        insertSession(sessionId = "sid-x", revoked = 1)      // revoked -> excluded
        insertSession(sessionId = "sid-other")               // different sid

        val result = repository.findByOidcSessionIdAndRevokedFalse("sid-x")

        assertThat(result).hasSize(2)
        assertThat(result).allMatch { !it.revoked && it.oidcSessionId == "sid-x" }
    }

    @Test
    fun `findByOidcSubjectAndOidcIssuerAndRevokedFalse filters by revoked and issuer`() {
        insertSession(subject = "sub-a", issuer = "https://iss-a")
        insertSession(subject = "sub-a", issuer = "https://iss-a", revoked = 1)   // revoked -> excluded
        insertSession(subject = "sub-a", issuer = "https://iss-b")               // different issuer

        val result = repository.findByOidcSubjectAndOidcIssuerAndRevokedFalse("sub-a", "https://iss-a")

        assertThat(result).hasSize(1)
        assertThat(result[0].revoked).isFalse()
        assertThat(result[0].oidcIssuer).isEqualTo("https://iss-a")
    }

    @Test
    fun `revokeById flips revoked to true`() {
        val id = insertSession(sessionId = "sid-r")

        repository.revokeById(id)

        // No longer returned by the non-revoked finder.
        assertThat(repository.findByOidcSessionIdAndRevokedFalse("sid-r")).isEmpty()
        assertThat(revokedFlag(id)).isEqualTo(1.toByte())
    }

    @Test
    fun `deleteRevokedCreatedBefore removes only revoked rows older than the cutoff`() {
        val cutoff = Instant.now().minus(1, ChronoUnit.DAYS)
        val revokedOld = insertSession(sessionId = "revoked-old", revoked = 1, createdAt = daysAgo(2))
        val revokedRecent = insertSession(sessionId = "revoked-recent", revoked = 1, createdAt = nowUtc())
        val activeOld = insertSession(sessionId = "active-old", revoked = 0, createdAt = daysAgo(2))

        repository.deleteRevokedCreatedBefore(cutoff)

        assertThat(existsById(revokedOld)).isFalse()      // revoked + old -> deleted
        assertThat(existsById(revokedRecent)).isTrue()    // revoked but recent -> kept
        assertThat(existsById(activeOld)).isTrue()        // old but not revoked -> kept
    }

    @Test
    fun `deleteCreatedBefore removes any row older than the cutoff regardless of revoked`() {
        val cutoff = Instant.now().minus(1, ChronoUnit.DAYS)
        val oldActive = insertSession(sessionId = "old-active", revoked = 0, createdAt = daysAgo(2))
        val oldRevoked = insertSession(sessionId = "old-revoked", revoked = 1, createdAt = daysAgo(2))
        val recent = insertSession(sessionId = "recent", revoked = 0, createdAt = nowUtc())

        repository.deleteCreatedBefore(cutoff)

        assertThat(existsById(oldActive)).isFalse()
        assertThat(existsById(oldRevoked)).isFalse()
        assertThat(existsById(recent)).isTrue()
    }

    // --- helpers ---

    private fun nowUtc(): LocalDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
    private fun daysAgo(days: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.now().minus(days, ChronoUnit.DAYS), ZoneOffset.UTC)
    private fun hoursAgo(hours: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.now().minus(hours, ChronoUnit.HOURS), ZoneOffset.UTC)

    private fun insertSession(
        userId: Long = this.userId,
        subject: String = "sub",
        issuer: String = this.issuer,
        sessionId: String? = null,
        idTokenHint: String? = null,
        createdAt: LocalDateTime = nowUtc(),
        revoked: Byte = 0,
    ): Long =
        dsl.insertInto(OIDC_SESSION)
            .set(OIDC_SESSION.USER_ID, userId)
            .set(OIDC_SESSION.OIDC_SUBJECT, subject)
            .set(OIDC_SESSION.OIDC_ISSUER, issuer)
            .set(OIDC_SESSION.OIDC_SESSION_ID, sessionId)
            .set(OIDC_SESSION.ID_TOKEN_HINT, idTokenHint)
            .set(OIDC_SESSION.CREATED_AT, createdAt)
            .set(OIDC_SESSION.REVOKED, revoked)
            .returningResult(OIDC_SESSION.ID).fetchOne()!!.get(OIDC_SESSION.ID)!!

    private fun existsById(id: Long): Boolean =
        dsl.fetchExists(dsl.selectFrom(OIDC_SESSION).where(OIDC_SESSION.ID.eq(id)))

    private fun revokedFlag(id: Long): Byte =
        dsl.select(OIDC_SESSION.REVOKED).from(OIDC_SESSION).where(OIDC_SESSION.ID.eq(id)).fetchOne()!!.value1()!!

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
