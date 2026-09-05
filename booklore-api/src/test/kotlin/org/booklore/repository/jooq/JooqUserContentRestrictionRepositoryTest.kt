package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.UserContentRestriction.USER_CONTENT_RESTRICTION
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.enums.ContentRestrictionMode
import org.booklore.model.enums.ContentRestrictionType
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqUserContentRestrictionRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqUserContentRestrictionRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(USER_CONTENT_RESTRICTION).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username).set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte()).set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!

    @Test
    fun `insert persists and findByUserId returns it as a DTO`() {
        val saved = repository.insert(userId, ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "Horror")
        assertThat(saved.id).isNotNull()
        assertThat(saved.userId).isEqualTo(userId)
        assertThat(saved.createdAt).isNotNull()

        val all = repository.findByUserId(userId)
        assertThat(all).hasSize(1)
        val r = all[0]
        assertThat(r.id).isEqualTo(saved.id)
        assertThat(r.restrictionType).isEqualTo(ContentRestrictionType.CATEGORY)
        assertThat(r.mode).isEqualTo(ContentRestrictionMode.EXCLUDE)
        assertThat(r.value).isEqualTo("Horror")
    }

    @Test
    fun `findById returns present or empty`() {
        val saved = repository.insert(userId, ContentRestrictionType.TAG, ContentRestrictionMode.ALLOW_ONLY, "epic")
        assertThat(repository.findById(saved.id)).isPresent
        assertThat(repository.findById(999_999L)).isEmpty
    }

    @Test
    fun `existsByUserIdAndRestrictionTypeAndValue reflects presence`() {
        repository.insert(userId, ContentRestrictionType.MOOD, ContentRestrictionMode.EXCLUDE, "tense")
        assertThat(repository.existsByUserIdAndRestrictionTypeAndValue(userId, ContentRestrictionType.MOOD, "tense")).isTrue()
        assertThat(repository.existsByUserIdAndRestrictionTypeAndValue(userId, ContentRestrictionType.MOOD, "cozy")).isFalse()
        assertThat(repository.existsByUserIdAndRestrictionTypeAndValue(userId, ContentRestrictionType.TAG, "tense")).isFalse()
    }

    @Test
    fun `insertAll then deleteByUserId clears them`() {
        repository.insertAll(userId, listOf(
            org.booklore.model.dto.ContentRestriction.builder()
                .restrictionType(ContentRestrictionType.CATEGORY).mode(ContentRestrictionMode.EXCLUDE).value("a").build(),
            org.booklore.model.dto.ContentRestriction.builder()
                .restrictionType(ContentRestrictionType.CATEGORY).mode(ContentRestrictionMode.EXCLUDE).value("b").build(),
        ))
        assertThat(repository.findByUserId(userId)).hasSize(2)

        val deleted = repository.deleteByUserId(userId)
        assertThat(deleted).isEqualTo(2)
        assertThat(repository.findByUserId(userId)).isEmpty()
    }

    @Test
    fun `deleteById removes a single restriction`() {
        val a = repository.insert(userId, ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "a")
        repository.insert(userId, ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "b")

        assertThat(repository.deleteById(a.id)).isEqualTo(1)
        assertThat(repository.existsById(a.id)).isFalse()
        assertThat(repository.findByUserId(userId)).hasSize(1)
    }

    @Test
    fun `deleting the owning user cascades to user_content_restriction`() {
        repository.insert(userId, ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "a")
        assertThat(dsl.fetchCount(USER_CONTENT_RESTRICTION)).isEqualTo(1)

        dsl.deleteFrom(USERS).where(USERS.ID.eq(userId)).execute()

        assertThat(dsl.fetchCount(USER_CONTENT_RESTRICTION)).isZero() // fk_ucr_user ON DELETE CASCADE (V111)
    }
}
