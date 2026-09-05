package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.booklore.jooq.tables.KoboUserSettings.KOBO_USER_SETTINGS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqKoboUserSettingsRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqKoboUserSettingsRepository
    @Autowired private lateinit var dsl: DSLContext

    private var u1: Long = 0
    private var u2: Long = 0
    private var u3: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(KOBO_USER_SETTINGS).execute()
        dsl.deleteFrom(USERS).execute()
        u1 = newUser("u1")
        u2 = newUser("u2")
        u3 = newUser("u3")
    }

    @Test
    fun `insert applies defaults and round-trips byte-boolean fields`() {
        val saved = repository.insert(u1, "tok-1", true)

        assertThat(saved.id).isPositive()
        assertThat(saved.userId).isEqualTo(u1)
        assertThat(saved.token).isEqualTo("tok-1")
        assertThat(saved.syncEnabled).isTrue()
        assertThat(saved.autoAddToShelf).isFalse()
        assertThat(saved.hardcoverSyncEnabled).isFalse()
        assertThat(saved.twoWayProgressSync).isFalse()
        assertThat(saved.progressMarkAsReadingThreshold).isCloseTo(1f, within(0.0001f))
        assertThat(saved.progressMarkAsFinishedThreshold).isCloseTo(99f, within(0.0001f))
    }

    @Test
    fun `findByUserId and findByToken locate the row, missing returns null`() {
        repository.insert(u1, "tok-7", false)

        assertThat(repository.findByUserId(u1)!!.token).isEqualTo("tok-7")
        assertThat(repository.findByToken("tok-7")!!.userId).isEqualTo(u1)
        assertThat(repository.findByUserId(u1 + 9999)).isNull()
        assertThat(repository.findByToken("nope")).isNull()
    }

    @Test
    fun `updateTokenByUserId changes only the token`() {
        repository.insert(u1, "old", true)

        val updated = repository.updateTokenByUserId(u1, "new")

        assertThat(updated.token).isEqualTo("new")
        assertThat(updated.syncEnabled).isTrue()
    }

    @Test
    fun `updateSettingsByUserId round-trips float thresholds and boolean flags`() {
        repository.insert(u1, "tok", false)

        val updated = repository.updateSettingsByUserId(u1, true, 0.7f, 0.95f, true, true)

        assertThat(updated.syncEnabled).isTrue()
        assertThat(updated.autoAddToShelf).isTrue()
        assertThat(updated.twoWayProgressSync).isTrue()
        assertThat(updated.progressMarkAsReadingThreshold).isCloseTo(0.7f, within(0.0001f))
        assertThat(updated.progressMarkAsFinishedThreshold).isCloseTo(0.95f, within(0.0001f))
    }

    @Test
    fun `findByAutoAddToShelfTrueAndSyncEnabledTrue returns only rows with both flags`() {
        repository.insert(u1, "a", true)
        repository.updateSettingsByUserId(u1, true, 1f, 99f, true, false)   // auto on + sync on -> included
        repository.insert(u2, "b", true)                                    // auto off -> excluded
        repository.insert(u3, "c", false)
        repository.updateSettingsByUserId(u3, false, 1f, 99f, true, false)  // auto on but sync off -> excluded

        val eligible = repository.findByAutoAddToShelfTrueAndSyncEnabledTrue()

        assertThat(eligible).extracting("userId").containsExactly(u1)
    }

    @Test
    fun `count and countByAutoAddToShelfTrue`() {
        repository.insert(u1, "a", true)
        repository.insert(u2, "b", true)
        repository.updateSettingsByUserId(u2, true, 1f, 99f, true, false)

        assertThat(repository.count()).isEqualTo(2L)
        assertThat(repository.countByAutoAddToShelfTrue()).isEqualTo(1L)
    }

    private fun newUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
