package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.UserSettings.USER_SETTINGS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqUserSettingRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqUserSettingRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(USER_SETTINGS).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username).set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte()).set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!

    @Test
    fun `upsertSetting inserts then updates in place`() {
        repository.upsertSetting(userId, "filter_mode", "AND")

        val byKey = repository.findByUserIdAndKey(userId, "filter_mode")
        assertThat(byKey).isPresent
        assertThat(byKey.get().settingValue).isEqualTo("AND")
        assertThat(repository.findByUserId(userId)).hasSize(1)

        repository.upsertSetting(userId, "filter_mode", "OR")
        assertThat(repository.findByUserIdAndKey(userId, "filter_mode").get().settingValue).isEqualTo("OR")
        // still a single row (updated, not duplicated)
        assertThat(dsl.fetchCount(USER_SETTINGS)).isEqualTo(1)
    }

    @Test
    fun `findByUserId returns all of the user's settings`() {
        repository.upsertSetting(userId, "filter_mode", "AND")
        repository.upsertSetting(userId, "filter_sorting_mode", "asc")

        val all = repository.findByUserId(userId)
        assertThat(all.map { it.settingKey to it.settingValue })
            .containsExactlyInAnyOrder("filter_mode" to "AND", "filter_sorting_mode" to "asc")
    }

    @Test
    fun `insertIfMissing inserts once and never overwrites an existing value`() {
        assertThat(repository.insertIfMissing(userId, "filter_mode", "AND")).isTrue()
        assertThat(repository.insertIfMissing(userId, "filter_mode", "OR")).isFalse()

        // original value preserved
        assertThat(repository.findByUserIdAndKey(userId, "filter_mode").get().settingValue).isEqualTo("AND")
        assertThat(dsl.fetchCount(USER_SETTINGS)).isEqualTo(1)
    }

    @Test
    fun `countBySettingKeyAndSettingValue counts matching rows`() {
        val otherUser = insertUser("other")
        repository.upsertSetting(userId, "hardcover_sync_enabled", "true")
        repository.upsertSetting(otherUser, "hardcover_sync_enabled", "true")
        repository.upsertSetting(insertUser("third"), "hardcover_sync_enabled", "false")

        assertThat(repository.countBySettingKeyAndSettingValue("hardcover_sync_enabled", "true")).isEqualTo(2)
        assertThat(repository.countBySettingKeyAndSettingValue("hardcover_sync_enabled", "false")).isEqualTo(1)
    }

    @Test
    fun `deleting the owning user cascades to user_settings`() {
        repository.upsertSetting(userId, "filter_mode", "AND")
        assertThat(dsl.fetchCount(USER_SETTINGS)).isEqualTo(1)

        dsl.deleteFrom(USERS).where(USERS.ID.eq(userId)).execute()

        assertThat(dsl.fetchCount(USER_SETTINGS)).isZero() // fk_user_setting_user ON DELETE CASCADE (V14)
    }
}
