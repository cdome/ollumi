package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.EmailRecipientV2.EMAIL_RECIPIENT_V2
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqEmailRecipientV2RepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqEmailRecipientV2Repository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var otherUserId: Long = 0

    @BeforeEach
    fun setUp() {
        // child-first: recipients reference users.
        dsl.deleteFrom(EMAIL_RECIPIENT_V2).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
        otherUserId = insertUser("other")
    }

    @Test
    fun `insert then findByIdAndUserId round-trips fields including the default flag`() {
        val saved = repository.insert(userId, "alice@test.com", "Alice", true)
        assertThat(saved.id).isPositive()

        val found = repository.findByIdAndUserId(saved.id, userId)!!
        assertThat(found.userId).isEqualTo(userId)
        assertThat(found.email).isEqualTo("alice@test.com")
        assertThat(found.name).isEqualTo("Alice")
        assertThat(found.isDefaultRecipient).isTrue()

        // default flag is physically stored as TINYINT 1 (bool -> byte mapping)
        val record = dsl.selectFrom(EMAIL_RECIPIENT_V2).where(EMAIL_RECIPIENT_V2.ID.eq(saved.id)).fetchOne()!!
        assertThat(record.isDefault).isEqualTo(1.toByte())
    }

    @Test
    fun `insert with default false stores a non-default recipient`() {
        val saved = repository.insert(userId, "bob@test.com", "Bob", false)
        val found = repository.findByIdAndUserId(saved.id, userId)!!
        assertThat(found.isDefaultRecipient).isFalse()

        val record = dsl.selectFrom(EMAIL_RECIPIENT_V2).where(EMAIL_RECIPIENT_V2.ID.eq(saved.id)).fetchOne()!!
        assertThat(record.isDefault).isEqualTo(0.toByte())
    }

    @Test
    fun `findByIdAndUserId returns null when absent`() {
        assertThat(repository.findByIdAndUserId(9999L, userId)).isNull()
    }

    @Test
    fun `findByIdAndUserId returns null for the wrong user`() {
        val saved = repository.insert(userId, "owned@test.com", "Owned", false)
        assertThat(repository.findByIdAndUserId(saved.id, otherUserId)).isNull()
    }

    @Test
    fun `findAllByUserId returns only that user's recipients ordered by id`() {
        val first = repository.insert(userId, "a@test.com", "A", false).id
        val second = repository.insert(userId, "b@test.com", "B", false).id
        repository.insert(otherUserId, "c@test.com", "C", false)

        val result = repository.findAllByUserId(userId)
        assertThat(result.map { it.id }).containsExactly(first, second)
    }

    @Test
    fun `findAll returns recipients across all users`() {
        repository.insert(userId, "a@test.com", "A", false)
        repository.insert(otherUserId, "b@test.com", "B", false)

        assertThat(repository.findAll()).hasSize(2)
    }

    @Test
    fun `findDefaultEmailRecipientByUserId returns the user's default recipient`() {
        repository.insert(userId, "plain@test.com", "Plain", false)
        val default = repository.insert(userId, "default@test.com", "Default", true).id
        // a default belonging to another user must not leak
        repository.insert(otherUserId, "otherdefault@test.com", "Other", true)

        val found = repository.findDefaultEmailRecipientByUserId(userId)!!
        assertThat(found.id).isEqualTo(default)
        assertThat(found.isDefaultRecipient).isTrue()
    }

    @Test
    fun `findDefaultEmailRecipientByUserId returns null when the user has no default`() {
        repository.insert(userId, "plain@test.com", "Plain", false)
        assertThat(repository.findDefaultEmailRecipientByUserId(userId)).isNull()
    }

    @Test
    fun `updateAllRecipientsToNonDefault clears defaults only for the target user`() {
        val userDefault = repository.insert(userId, "u@test.com", "U", true).id
        val otherDefault = repository.insert(otherUserId, "o@test.com", "O", true).id

        repository.updateAllRecipientsToNonDefault(userId)

        assertThat(repository.findByIdAndUserId(userDefault, userId)!!.isDefaultRecipient).isFalse()
        // the other user's default is untouched
        assertThat(repository.findByIdAndUserId(otherDefault, otherUserId)!!.isDefaultRecipient).isTrue()
    }

    @Test
    fun `markDefaultById marks a recipient as default`() {
        val saved = repository.insert(userId, "m@test.com", "M", false)
        assertThat(saved.isDefaultRecipient).isFalse()

        repository.markDefaultById(saved.id)

        assertThat(repository.findByIdAndUserId(saved.id, userId)!!.isDefaultRecipient).isTrue()
    }

    @Test
    fun `update round-trips changed fields`() {
        val saved = repository.insert(userId, "old@test.com", "Old", false)

        val updated = repository.update(saved.id, "new@test.com", "New", true)

        assertThat(updated.email).isEqualTo("new@test.com")
        assertThat(updated.name).isEqualTo("New")
        assertThat(updated.isDefaultRecipient).isTrue()

        val reread = repository.findByIdAndUserId(saved.id, userId)!!
        assertThat(reread.email).isEqualTo("new@test.com")
        assertThat(reread.name).isEqualTo("New")
        assertThat(reread.isDefaultRecipient).isTrue()
    }

    @Test
    fun `count reflects the number of stored recipients`() {
        assertThat(repository.count()).isZero()
        repository.insert(userId, "a@test.com", "A", false)
        repository.insert(otherUserId, "b@test.com", "B", false)
        assertThat(repository.count()).isEqualTo(2L)
    }

    @Test
    fun `deleteById removes the row`() {
        val saved = repository.insert(userId, "doomed@test.com", "Doomed", false)
        repository.deleteById(saved.id)
        assertThat(repository.findByIdAndUserId(saved.id, userId)).isNull()
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
