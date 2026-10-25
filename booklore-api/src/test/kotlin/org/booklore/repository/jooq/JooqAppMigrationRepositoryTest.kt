package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.AppMigration.APP_MIGRATION
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class JooqAppMigrationRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqAppMigrationRepository
    @Autowired private lateinit var dsl: DSLContext

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(APP_MIGRATION).execute()
    }

    @Test
    fun `existsByKey is false before insert, true after`() {
        assertThat(repository.existsByKey("migrate-x")).isFalse()

        repository.insert("migrate-x", LocalDateTime.of(2026, 1, 2, 3, 4, 5), "does x")

        assertThat(repository.existsByKey("migrate-x")).isTrue()
        val row = dsl.selectFrom(APP_MIGRATION).where(APP_MIGRATION.MIGRATION_KEY.eq("migrate-x")).fetchOne()!!
        assertThat(row.executedAt).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4, 5))
        assertThat(row.description).isEqualTo("does x")
    }

    @Test
    fun `insert allows null description`() {
        repository.insert("migrate-y", LocalDateTime.now(), null)
        val row = dsl.selectFrom(APP_MIGRATION).where(APP_MIGRATION.MIGRATION_KEY.eq("migrate-y")).fetchOne()!!
        assertThat(row.get(APP_MIGRATION.DESCRIPTION) as String?).isNull()
    }

    @Test
    fun `existsByKey is scoped to the exact key`() {
        repository.insert("migrate-a", LocalDateTime.now(), null)
        assertThat(repository.existsByKey("migrate-a")).isTrue()
        assertThat(repository.existsByKey("migrate-b")).isFalse()
    }
}
