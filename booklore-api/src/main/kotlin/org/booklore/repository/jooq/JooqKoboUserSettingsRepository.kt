package org.booklore.repository.jooq

import org.booklore.jooq.tables.KoboUserSettings.KOBO_USER_SETTINGS
import org.booklore.jooq.tables.records.KoboUserSettingsRecord
import org.booklore.repository.jooq.dto.KoboUserSettings
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqKoboUserSettingsRepository(private val dsl: DSLContext) {

    private val t = KOBO_USER_SETTINGS

    fun findByUserId(userId: Long): KoboUserSettings? =
        dsl.selectFrom(t).where(t.USER_ID.eq(userId)).fetchOne()?.let(::toDto)

    fun findByToken(token: String): KoboUserSettings? =
        dsl.selectFrom(t).where(t.TOKEN.eq(token)).fetchOne()?.let(::toDto)

    fun findByAutoAddToShelfTrueAndSyncEnabledTrue(): List<KoboUserSettings> =
        dsl.selectFrom(t)
            .where(t.AUTO_ADD_TO_SHELF.eq(1.toByte()).and(t.SYNC_ENABLED.eq(1.toByte())))
            .fetch().map(::toDto)

    fun count(): Long = dsl.fetchCount(t).toLong()

    fun countByAutoAddToShelfTrue(): Long =
        dsl.fetchCount(t, t.AUTO_ADD_TO_SHELF.eq(1.toByte())).toLong()

    /** Insert a new settings row with the entity's builder defaults for everything but the given fields. */
    fun insert(userId: Long, token: String, syncEnabled: Boolean): KoboUserSettings {
        dsl.insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.TOKEN, token)
            .set(t.SYNC_ENABLED, syncEnabled.toByteFlag())
            .set(t.PROGRESS_MARK_AS_READING_THRESHOLD, 1.0)
            .set(t.PROGRESS_MARK_AS_FINISHED_THRESHOLD, 99.0)
            .set(t.AUTO_ADD_TO_SHELF, 0.toByte())
            .set(t.HARDCOVER_SYNC_ENABLED, 0.toByte())
            .set(t.TWO_WAY_PROGRESS_SYNC, 0.toByte())
            .execute()
        return findByUserId(userId)!!
    }

    fun updateTokenByUserId(userId: Long, token: String): KoboUserSettings {
        dsl.update(t).set(t.TOKEN, token).where(t.USER_ID.eq(userId)).execute()
        return findByUserId(userId)!!
    }

    fun updateSettingsByUserId(
        userId: Long,
        syncEnabled: Boolean,
        readingThreshold: Float?,
        finishedThreshold: Float?,
        autoAddToShelf: Boolean,
        twoWayProgressSync: Boolean,
    ): KoboUserSettings {
        dsl.update(t)
            .set(t.SYNC_ENABLED, syncEnabled.toByteFlag())
            .set(t.PROGRESS_MARK_AS_READING_THRESHOLD, readingThreshold?.toDouble())
            .set(t.PROGRESS_MARK_AS_FINISHED_THRESHOLD, finishedThreshold?.toDouble())
            .set(t.AUTO_ADD_TO_SHELF, autoAddToShelf.toByteFlag())
            .set(t.TWO_WAY_PROGRESS_SYNC, twoWayProgressSync.toByteFlag())
            .where(t.USER_ID.eq(userId))
            .execute()
        return findByUserId(userId)!!
    }

    private fun toDto(r: KoboUserSettingsRecord): KoboUserSettings =
        KoboUserSettings(
            id = r.id,
            userId = r.userId,
            token = r.token,
            syncEnabled = r.get(t.SYNC_ENABLED).toBooleanFlag(),
            progressMarkAsReadingThreshold = r.get(t.PROGRESS_MARK_AS_READING_THRESHOLD)?.toFloat(),
            progressMarkAsFinishedThreshold = r.get(t.PROGRESS_MARK_AS_FINISHED_THRESHOLD)?.toFloat(),
            autoAddToShelf = r.get(t.AUTO_ADD_TO_SHELF).toBooleanFlag(),
            hardcoverApiKey = r.hardcoverApiKey,
            hardcoverSyncEnabled = r.get(t.HARDCOVER_SYNC_ENABLED).toBooleanFlag(),
            twoWayProgressSync = r.get(t.TWO_WAY_PROGRESS_SYNC).toBooleanFlag(),
        )

    private fun Boolean.toByteFlag(): Byte = if (this) 1 else 0
    private fun Byte?.toBooleanFlag(): Boolean = this != null && this.toInt() != 0
}
