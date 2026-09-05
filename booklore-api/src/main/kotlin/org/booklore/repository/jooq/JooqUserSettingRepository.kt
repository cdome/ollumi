package org.booklore.repository.jooq

import org.booklore.jooq.tables.UserSettings.USER_SETTINGS
import org.booklore.repository.jooq.dto.UserSettingRow
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

/**
 * jOOQ replacement for the JPA UserSettingRepository and the BookLoreUserEntity.settings
 * @OneToMany cascade. The old write path was "mutate user.getSettings() + save(user)"; here
 * each setting is upserted directly by (user_id, setting_key) (a UNIQUE key). created_at /
 * updated_at are stamped explicitly (were @CreationTimestamp / @UpdateTimestamp).
 */
@Repository
class JooqUserSettingRepository(private val dsl: DSLContext) {

    private val t = USER_SETTINGS

    fun findByUserId(userId: Long): List<UserSettingRow> =
        dsl.select(t.SETTING_KEY, t.SETTING_VALUE)
            .from(t)
            .where(t.USER_ID.eq(userId))
            .fetch { UserSettingRow(it.get(t.SETTING_KEY)!!, it.get(t.SETTING_VALUE)!!) }

    fun findByUserIdAndKey(userId: Long, key: String): Optional<UserSettingRow> =
        Optional.ofNullable(
            dsl.select(t.SETTING_KEY, t.SETTING_VALUE)
                .from(t)
                .where(t.USER_ID.eq(userId).and(t.SETTING_KEY.eq(key)))
                .fetchOne()
                ?.let { UserSettingRow(it.get(t.SETTING_KEY)!!, it.get(t.SETTING_VALUE)!!) }
        )

    /** Insert or overwrite the (user, key) setting value. */
    fun upsertSetting(userId: Long, key: String, value: String) {
        val now = LocalDateTime.now()
        val updated = dsl.update(t)
            .set(t.SETTING_VALUE, value)
            .set(t.UPDATED_AT, now)
            .where(t.USER_ID.eq(userId).and(t.SETTING_KEY.eq(key)))
            .execute()
        if (updated == 0) {
            dsl.insertInto(t)
                .set(t.USER_ID, userId)
                .set(t.SETTING_KEY, key)
                .set(t.SETTING_VALUE, value)
                .set(t.CREATED_AT, now)
                .set(t.UPDATED_AT, now)
                .execute()
        }
    }

    /** Insert the default only if the (user, key) setting does not exist yet; never overwrites an existing value. */
    fun insertIfMissing(userId: Long, key: String, value: String): Boolean {
        if (dsl.fetchExists(t, t.USER_ID.eq(userId).and(t.SETTING_KEY.eq(key)))) {
            return false
        }
        val now = LocalDateTime.now()
        dsl.insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.SETTING_KEY, key)
            .set(t.SETTING_VALUE, value)
            .set(t.CREATED_AT, now)
            .set(t.UPDATED_AT, now)
            .execute()
        return true
    }

    fun countBySettingKeyAndSettingValue(key: String, value: String): Long =
        dsl.fetchCount(t, t.SETTING_KEY.eq(key).and(t.SETTING_VALUE.eq(value))).toLong()
}
