package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.CustomFont.CUSTOM_FONT
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.enums.FontFormat
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class JooqCustomFontRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repository: JooqCustomFontRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var otherUserId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(CUSTOM_FONT).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
        otherUserId = insertUser("stranger")
    }

    @Test
    fun `insert returns the generated row with all fields`() {
        val uploadedAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        val saved = repository.insert(userId, "My Font", "user_1_font_abc.ttf", "MyFont.ttf", FontFormat.TTF, 4096L, uploadedAt)

        assertThat(saved.id).isPositive()
        assertThat(saved.userId).isEqualTo(userId)
        assertThat(saved.fontName).isEqualTo("My Font")
        assertThat(saved.fileName).isEqualTo("user_1_font_abc.ttf")
        assertThat(saved.originalFileName).isEqualTo("MyFont.ttf")
        assertThat(saved.format).isEqualTo(FontFormat.TTF)
        assertThat(saved.fileSize).isEqualTo(4096L)
        assertThat(saved.uploadedAt).isEqualTo(uploadedAt)
    }

    @Test
    fun `findByUserId returns only the user's fonts ordered by id`() {
        repository.insert(userId, "A", "a.ttf", "a.ttf", FontFormat.TTF, 1L, LocalDateTime.now())
        repository.insert(userId, "B", "b.otf", "b.otf", FontFormat.OTF, 2L, LocalDateTime.now())
        repository.insert(otherUserId, "C", "c.woff", "c.woff", FontFormat.WOFF, 3L, LocalDateTime.now())

        val fonts = repository.findByUserId(userId)

        assertThat(fonts).extracting("fontName").containsExactly("A", "B")
        assertThat(fonts.map { it.id }).isSorted()
    }

    @Test
    fun `countByUserId counts only the user's fonts`() {
        repository.insert(userId, "A", "a.ttf", "a.ttf", FontFormat.TTF, 1L, LocalDateTime.now())
        repository.insert(userId, "B", "b.otf", "b.otf", FontFormat.OTF, 2L, LocalDateTime.now())
        repository.insert(otherUserId, "C", "c.woff", "c.woff", FontFormat.WOFF, 3L, LocalDateTime.now())

        assertThat(repository.countByUserId(userId)).isEqualTo(2)
        assertThat(repository.countByUserId(otherUserId)).isEqualTo(1)
    }

    @Test
    fun `findByIdAndUserId enforces ownership`() {
        val saved = repository.insert(userId, "A", "a.ttf", "a.ttf", FontFormat.TTF, 1L, LocalDateTime.now())

        assertThat(repository.findByIdAndUserId(saved.id, userId)).isNotNull()
        assertThat(repository.findByIdAndUserId(saved.id, otherUserId)).isNull()
    }

    @Test
    fun `deleteById removes the row`() {
        val saved = repository.insert(userId, "A", "a.ttf", "a.ttf", FontFormat.TTF, 1L, LocalDateTime.now())

        repository.deleteById(saved.id)

        assertThat(repository.findByIdAndUserId(saved.id, userId)).isNull()
        assertThat(repository.countByUserId(userId)).isZero()
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID)
            .fetchOne()!!.get(USERS.ID)!!
}
