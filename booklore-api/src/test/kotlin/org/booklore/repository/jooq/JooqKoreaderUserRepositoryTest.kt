package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.KoreaderUser.KOREADER_USER
import org.booklore.jooq.tables.Users.USERS
import org.booklore.repository.jooq.dto.KoreaderUserRow
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqKoreaderUserRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqKoreaderUserRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(KOREADER_USER).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username).set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte()).set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!

    @Test
    fun `save inserts then findByUsername and findByBookLoreUserId return it`() {
        val saved = repository.save(KoreaderUserRow(
            username = "kv", password = "raw", passwordMD5 = "md5",
            syncEnabled = true, syncWithBookloreReader = true, bookLoreUserId = userId,
        ))
        assertThat(saved.id).isNotNull()
        assertThat(saved.createdAt).isNotNull()

        val byName = repository.findByUsername("kv")
        assertThat(byName).isPresent
        val r = byName.get()
        assertThat(r.password).isEqualTo("raw")
        assertThat(r.passwordMD5).isEqualTo("md5")
        assertThat(r.syncEnabled).isTrue()
        assertThat(r.syncWithBookloreReader).isTrue()
        assertThat(r.bookLoreUserId).isEqualTo(userId)

        assertThat(repository.findByBookLoreUserId(userId)).isPresent
    }

    @Test
    fun `save defaults sync flags to false`() {
        repository.save(KoreaderUserRow(username = "kv2", password = "p", passwordMD5 = "m", bookLoreUserId = userId))
        val r = repository.findByUsername("kv2").get()
        assertThat(r.syncEnabled).isFalse()
        assertThat(r.syncWithBookloreReader).isFalse()
    }

    @Test
    fun `findByUsername is empty for unknown`() {
        assertThat(repository.findByUsername("nope")).isEmpty
    }

    @Test
    fun `save updates in place when id is present`() {
        val row = repository.save(KoreaderUserRow(username = "kv", password = "raw", passwordMD5 = "md5", bookLoreUserId = userId))

        row.username = "kv-renamed"
        row.syncEnabled = true
        repository.save(row)

        val after = repository.findByBookLoreUserId(userId).get()
        assertThat(after.id).isEqualTo(row.id)
        assertThat(after.username).isEqualTo("kv-renamed")
        assertThat(after.syncEnabled).isTrue()
        assertThat(dsl.fetchCount(KOREADER_USER)).isEqualTo(1)
    }

    @Test
    fun `count reflects rows`() {
        assertThat(repository.count()).isZero()
        repository.save(KoreaderUserRow(username = "kv", password = "p", passwordMD5 = "m", bookLoreUserId = userId))
        assertThat(repository.count()).isEqualTo(1)
    }

    @Test
    fun `deleting the owning user cascades to koreader_user`() {
        repository.save(KoreaderUserRow(username = "kv", password = "p", passwordMD5 = "m", bookLoreUserId = userId))
        assertThat(repository.count()).isEqualTo(1)

        dsl.deleteFrom(USERS).where(USERS.ID.eq(userId)).execute()

        assertThat(repository.count()).isZero() // ON DELETE CASCADE (V134)
    }
}
