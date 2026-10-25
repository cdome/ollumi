package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.OpdsUserV2.OPDS_USER_V2
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.enums.OpdsSortOrder
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqOpdsUserV2RepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqOpdsUserV2Repository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var otherUserId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(OPDS_USER_V2).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
        otherUserId = insertUser("other")
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!

    @Test
    fun `insert then findById returns the dto with defaults`() {
        val created = repository.insert(userId, "alice", "hash-a", OpdsSortOrder.RECENT)

        assertThat(created.id).isGreaterThan(0)
        assertThat(created.userId).isEqualTo(userId)
        assertThat(created.username).isEqualTo("alice")
        assertThat(created.passwordHash).isEqualTo("hash-a")
        assertThat(created.sortOrder).isEqualTo(OpdsSortOrder.RECENT)

        val found = repository.findById(created.id)
        assertThat(found).isPresent
        assertThat(found.get().username).isEqualTo("alice")
    }

    @Test
    fun `insert persists a non-default sort order`() {
        val created = repository.insert(userId, "bob", "hash-b", OpdsSortOrder.TITLE_ASC)
        assertThat(repository.findById(created.id).get().sortOrder).isEqualTo(OpdsSortOrder.TITLE_ASC)
    }

    @Test
    fun `findById is empty for unknown id`() {
        assertThat(repository.findById(999_999L)).isEmpty
    }

    @Test
    fun `findByUsername returns the matching credential`() {
        repository.insert(userId, "carol", "hash-c", OpdsSortOrder.RECENT)

        val found = repository.findByUsername("carol")
        assertThat(found).isPresent
        assertThat(found.get().passwordHash).isEqualTo("hash-c")
        assertThat(repository.findByUsername("nobody")).isEmpty
    }

    @Test
    fun `findByUserId returns only that user's credentials`() {
        repository.insert(userId, "u1-a", "h", OpdsSortOrder.RECENT)
        repository.insert(userId, "u1-b", "h", OpdsSortOrder.RECENT)
        repository.insert(otherUserId, "u2-a", "h", OpdsSortOrder.RECENT)

        val forOwner = repository.findByUserId(userId)
        assertThat(forOwner).hasSize(2)
        assertThat(forOwner.map { it.username }).containsExactlyInAnyOrder("u1-a", "u1-b")
        assertThat(repository.findByUserId(otherUserId)).hasSize(1)
    }

    @Test
    fun `count reflects all rows`() {
        assertThat(repository.count()).isZero()
        repository.insert(userId, "x", "h", OpdsSortOrder.RECENT)
        repository.insert(otherUserId, "y", "h", OpdsSortOrder.RECENT)
        assertThat(repository.count()).isEqualTo(2)
    }

    @Test
    fun `updateSortOrder changes only the sort order`() {
        val created = repository.insert(userId, "dana", "hash-d", OpdsSortOrder.RECENT)

        val updated = repository.updateSortOrder(created.id, OpdsSortOrder.AUTHOR_DESC)

        assertThat(updated.sortOrder).isEqualTo(OpdsSortOrder.AUTHOR_DESC)
        assertThat(updated.username).isEqualTo("dana")
        assertThat(updated.passwordHash).isEqualTo("hash-d")
        assertThat(repository.findById(created.id).get().sortOrder).isEqualTo(OpdsSortOrder.AUTHOR_DESC)
    }

    @Test
    fun `deleteById removes the row`() {
        val created = repository.insert(userId, "erin", "h", OpdsSortOrder.RECENT)
        repository.deleteById(created.id)
        assertThat(repository.findById(created.id)).isEmpty
        assertThat(repository.count()).isZero()
    }
}
