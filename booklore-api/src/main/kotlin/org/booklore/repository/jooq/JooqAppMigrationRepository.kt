package org.booklore.repository.jooq

import org.booklore.jooq.tables.AppMigration.APP_MIGRATION
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** Tracks one-time application-level data migrations (table app_migration). */
@Repository
class JooqAppMigrationRepository(private val dsl: DSLContext) {

    private val t = APP_MIGRATION

    fun existsByKey(key: String): Boolean =
        dsl.fetchExists(t, t.MIGRATION_KEY.eq(key))

    fun insert(key: String, executedAt: LocalDateTime, description: String?) {
        dsl.insertInto(t)
            .set(t.MIGRATION_KEY, key)
            .set(t.EXECUTED_AT, executedAt)
            .set(t.DESCRIPTION, description)
            .execute()
    }
}
