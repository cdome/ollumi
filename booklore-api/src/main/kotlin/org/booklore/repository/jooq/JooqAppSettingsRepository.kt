package org.booklore.repository.jooq

import org.booklore.jooq.tables.AppSettings.APP_SETTINGS
import org.booklore.jooq.tables.records.AppSettingsRecord
import org.booklore.repository.jooq.dto.AppSetting
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqAppSettingsRepository(private val dsl: DSLContext) {

    private val t = APP_SETTINGS

    fun findByName(name: String): AppSetting? =
        dsl.selectFrom(t).where(t.NAME.eq(name)).fetchOne()?.let(::toDto)

    fun findAll(): List<AppSetting> =
        dsl.selectFrom(t).fetch().map(::toDto)

    /** Upsert keyed by name: update val where the name matches, else insert a new row. */
    fun upsertByName(name: String, value: String?) {
        val updated = dsl.update(t).set(t.VAL, value).where(t.NAME.eq(name)).execute()
        if (updated == 0) {
            dsl.insertInto(t).set(t.NAME, name).set(t.VAL, value).execute()
        }
    }

    private fun toDto(r: AppSettingsRecord): AppSetting =
        AppSetting(id = r.id, name = r.name, value = r.get(t.VAL))
}
