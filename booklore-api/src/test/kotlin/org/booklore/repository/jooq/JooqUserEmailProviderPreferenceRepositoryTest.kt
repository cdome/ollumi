package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.EmailProviderV2.EMAIL_PROVIDER_V2
import org.booklore.jooq.tables.UserEmailProviderPreference.USER_EMAIL_PROVIDER_PREFERENCE
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqUserEmailProviderPreferenceRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqUserEmailProviderPreferenceRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var otherUserId: Long = 0
    private var providerA: Long = 0
    private var providerB: Long = 0

    @BeforeEach
    fun setUp() {
        // child-first: preferences reference both users and providers; providers reference users.
        dsl.deleteFrom(USER_EMAIL_PROVIDER_PREFERENCE).execute()
        dsl.deleteFrom(EMAIL_PROVIDER_V2).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
        otherUserId = insertUser("other")
        providerA = insertProvider(userId, "A")
        providerB = insertProvider(userId, "B")
    }

    @Test
    fun `upsert inserts when the user has no preference then findByUserId returns it`() {
        repository.upsertDefaultProvider(userId, providerA)

        val found = repository.findByUserId(userId)
        assertThat(found).isPresent
        assertThat(found.get().userId).isEqualTo(userId)
        assertThat(found.get().defaultProviderId).isEqualTo(providerA)
        assertThat(found.get().id).isGreaterThan(0)
    }

    @Test
    fun `upsert updates the existing row instead of inserting a second (unique user_id)`() {
        repository.upsertDefaultProvider(userId, providerA)
        val firstId = repository.findByUserId(userId).get().id

        repository.upsertDefaultProvider(userId, providerB)

        val found = repository.findByUserId(userId).get()
        assertThat(found.id).isEqualTo(firstId)                 // same row
        assertThat(found.defaultProviderId).isEqualTo(providerB) // new value
        assertThat(dsl.fetchCount(USER_EMAIL_PROVIDER_PREFERENCE)).isEqualTo(1)
    }

    @Test
    fun `findByUserId returns empty for a user without a preference`() {
        assertThat(repository.findByUserId(otherUserId)).isEmpty
    }

    @Test
    fun `findAllByDefaultProviderId returns only preferences pointing at that provider`() {
        repository.upsertDefaultProvider(userId, providerA)
        repository.upsertDefaultProvider(otherUserId, providerB)

        val usingA = repository.findAllByDefaultProviderId(providerA)
        assertThat(usingA).hasSize(1)
        assertThat(usingA[0].userId).isEqualTo(userId)

        val usingB = repository.findAllByDefaultProviderId(providerB)
        assertThat(usingB).hasSize(1)
        assertThat(usingB[0].userId).isEqualTo(otherUserId)
    }

    @Test
    fun `updateDefaultProviderById changes the default for that row only`() {
        repository.upsertDefaultProvider(userId, providerA)
        val id = repository.findByUserId(userId).get().id

        repository.updateDefaultProviderById(id, providerB)

        assertThat(repository.findByUserId(userId).get().defaultProviderId).isEqualTo(providerB)
    }

    @Test
    fun `deleteById removes the preference`() {
        repository.upsertDefaultProvider(userId, providerA)
        val id = repository.findByUserId(userId).get().id

        repository.deleteById(id)

        assertThat(repository.findByUserId(userId)).isEmpty
    }

    // --- helpers ---

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!

    private fun insertProvider(ownerId: Long, name: String): Long =
        dsl.insertInto(EMAIL_PROVIDER_V2)
            .set(EMAIL_PROVIDER_V2.USER_ID, ownerId)
            .set(EMAIL_PROVIDER_V2.NAME, name)
            .set(EMAIL_PROVIDER_V2.HOST, "smtp.example.com")
            .set(EMAIL_PROVIDER_V2.PORT, 465)
            .set(EMAIL_PROVIDER_V2.USERNAME, "sender@example.com")
            .set(EMAIL_PROVIDER_V2.PASSWORD, "s3cr3t")
            .set(EMAIL_PROVIDER_V2.AUTH, 1.toByte())
            .set(EMAIL_PROVIDER_V2.START_TLS, 0.toByte())
            .returningResult(EMAIL_PROVIDER_V2.ID).fetchOne()!!.get(EMAIL_PROVIDER_V2.ID)!!
}
