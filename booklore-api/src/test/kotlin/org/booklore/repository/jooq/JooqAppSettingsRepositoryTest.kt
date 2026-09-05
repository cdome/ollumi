package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.AppSettings.APP_SETTINGS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqAppSettingsRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqAppSettingsRepository
    @Autowired private lateinit var dsl: DSLContext

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(APP_SETTINGS).execute()
    }

    @Test
    fun `upsertByName inserts a new row then findByName returns it`() {
        repository.upsertByName("theme", "dark")

        val found = repository.findByName("theme")
        assertThat(found).isNotNull
        assertThat(found!!.name).isEqualTo("theme")
        assertThat(found.value).isEqualTo("dark")
        assertThat(found.id).isGreaterThan(0)
    }

    @Test
    fun `upsertByName updates the existing row without creating a duplicate`() {
        repository.upsertByName("theme", "dark")
        val firstId = repository.findByName("theme")!!.id

        repository.upsertByName("theme", "light")

        val found = repository.findByName("theme")!!
        assertThat(found.id).isEqualTo(firstId)      // same row
        assertThat(found.value).isEqualTo("light")   // new value
        assertThat(dsl.fetchCount(APP_SETTINGS)).isEqualTo(1)
    }

    @Test
    fun `findByName returns null for an unknown key`() {
        assertThat(repository.findByName("missing")).isNull()
    }

    @Test
    fun `upsertByName can store a null value`() {
        repository.upsertByName("maybe", null)

        val found = repository.findByName("maybe")
        assertThat(found).isNotNull
        assertThat(found!!.value).isNull()
    }

    @Test
    fun `findAll returns every setting`() {
        repository.upsertByName("a", "1")
        repository.upsertByName("b", "2")
        repository.upsertByName("c", "3")

        val all = repository.findAll()

        assertThat(all).hasSize(3)
        assertThat(all.map { it.name }).containsExactlyInAnyOrder("a", "b", "c")
        assertThat(all.associate { it.name to it.value })
            .containsEntry("a", "1").containsEntry("b", "2").containsEntry("c", "3")
    }
}
