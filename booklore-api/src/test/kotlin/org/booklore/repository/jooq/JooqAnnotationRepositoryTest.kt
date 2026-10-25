package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Annotations.ANNOTATIONS
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class JooqAnnotationRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqAnnotationRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var otherUserId: Long = 0
    private var bookId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(ANNOTATIONS).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY).execute()
        dsl.deleteFrom(USERS).execute()

        userId = insertUser("owner")
        otherUserId = insertUser("stranger")
        val libId = dsl.insertInto(LIBRARY).set(LIBRARY.NAME, "Library")
            .returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!
        bookId = dsl.insertInto(BOOK).set(BOOK.LIBRARY_ID, libId).set(BOOK.ADDED_ON, LocalDateTime.now())
            .returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
    }

    @Test
    fun `insert returns the persisted annotation`() {
        val a = repository.insert(bookId, userId, "cfi(/6/4)", "highlighted text", "#FFFF00", "highlight", "a note", "Chapter 1")

        assertThat(a.id).isPositive()
        assertThat(a.bookId).isEqualTo(bookId)
        assertThat(a.userId).isEqualTo(userId)
        assertThat(a.cfi).isEqualTo("cfi(/6/4)")
        assertThat(a.text).isEqualTo("highlighted text")
        assertThat(a.color).isEqualTo("#FFFF00")
        assertThat(a.style).isEqualTo("highlight")
        assertThat(a.note).isEqualTo("a note")
        assertThat(a.chapterTitle).isEqualTo("Chapter 1")
        assertThat(a.createdAt).isNotNull()
    }

    @Test
    fun `insert allows null note and chapterTitle`() {
        val a = repository.insert(bookId, userId, "cfi", "text", "#FFFF00", "highlight", null, null)
        assertThat(a.note).isNull()
        assertThat(a.chapterTitle).isNull()
    }

    @Test
    fun `update writes mutable fields, bumps version, keeps created_at`() {
        val created = repository.insert(bookId, userId, "cfi", "text", "#FFFF00", "highlight", null, null)
        assertThat(dsl.select(ANNOTATIONS.VERSION).from(ANNOTATIONS).fetchOne(ANNOTATIONS.VERSION)).isEqualTo(0L)

        created.color = "#00FF00"
        created.style = "underline"
        created.note = "edited"
        val updated = repository.update(created)

        assertThat(updated.color).isEqualTo("#00FF00")
        assertThat(updated.style).isEqualTo("underline")
        assertThat(updated.note).isEqualTo("edited")
        assertThat(updated.createdAt).isEqualTo(created.createdAt)
        assertThat(dsl.select(ANNOTATIONS.VERSION).from(ANNOTATIONS).fetchOne(ANNOTATIONS.VERSION)).isEqualTo(1L)
    }

    @Test
    fun `list is ordered by created_at desc and scoped by user`() {
        val first = repository.insert(bookId, userId, "a", "t", "#FFFF00", "highlight", null, null)
        val second = repository.insert(bookId, userId, "b", "t", "#FFFF00", "highlight", null, null)
        repository.insert(bookId, otherUserId, "c", "t", "#FFFF00", "highlight", null, null)
        dsl.update(ANNOTATIONS).set(ANNOTATIONS.CREATED_AT, LocalDateTime.of(2026, 1, 1, 0, 0))
            .where(ANNOTATIONS.ID.eq(first.id)).execute()
        dsl.update(ANNOTATIONS).set(ANNOTATIONS.CREATED_AT, LocalDateTime.of(2026, 1, 2, 0, 0))
            .where(ANNOTATIONS.ID.eq(second.id)).execute()

        val list = repository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId)

        assertThat(list.map { it.cfi }).containsExactly("b", "a")
    }

    @Test
    fun `existsByCfi is user and book scoped`() {
        repository.insert(bookId, userId, "dupcfi", "t", "#FFFF00", "highlight", null, null)

        assertThat(repository.existsByCfiAndBookIdAndUserId("dupcfi", bookId, userId)).isTrue()
        assertThat(repository.existsByCfiAndBookIdAndUserId("dupcfi", bookId, otherUserId)).isFalse()
        assertThat(repository.existsByCfiAndBookIdAndUserId("nope", bookId, userId)).isFalse()
    }

    @Test
    fun `findByIdAndUserId enforces ownership, delete removes row`() {
        val a = repository.insert(bookId, userId, "cfi", "t", "#FFFF00", "highlight", null, null)
        assertThat(repository.findByIdAndUserId(a.id, otherUserId)).isNull()

        repository.deleteById(a.id)

        assertThat(repository.findByIdAndUserId(a.id, userId)).isNull()
        assertThat(dsl.fetchCount(ANNOTATIONS)).isZero()
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
