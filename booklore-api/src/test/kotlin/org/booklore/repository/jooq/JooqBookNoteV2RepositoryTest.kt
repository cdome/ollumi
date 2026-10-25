package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookNotesV2.BOOK_NOTES_V2
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class JooqBookNoteV2RepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqBookNoteV2Repository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var otherUserId: Long = 0
    private var bookId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(BOOK_NOTES_V2).execute()
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
    fun `insert returns the persisted note`() {
        val n = repository.insert(bookId, userId, "cfi(/6/4)", "selected", "my content", "#FFC107", "Chapter 1")

        assertThat(n.id).isPositive()
        assertThat(n.bookId).isEqualTo(bookId)
        assertThat(n.userId).isEqualTo(userId)
        assertThat(n.cfi).isEqualTo("cfi(/6/4)")
        assertThat(n.selectedText).isEqualTo("selected")
        assertThat(n.noteContent).isEqualTo("my content")
        assertThat(n.color).isEqualTo("#FFC107")
        assertThat(n.chapterTitle).isEqualTo("Chapter 1")
        assertThat(n.createdAt).isNotNull()
    }

    @Test
    fun `insert allows null selectedText and chapterTitle`() {
        val n = repository.insert(bookId, userId, "cfi", null, "content", "#FFC107", null)
        assertThat(n.selectedText).isNull()
        assertThat(n.chapterTitle).isNull()
    }

    @Test
    fun `update writes mutable fields, bumps version, keeps created_at`() {
        val created = repository.insert(bookId, userId, "cfi", "sel", "content", "#FFC107", "ch")
        assertThat(dsl.select(BOOK_NOTES_V2.VERSION).from(BOOK_NOTES_V2).fetchOne(BOOK_NOTES_V2.VERSION)).isEqualTo(0L)

        created.noteContent = "edited"
        created.color = "#00FF00"
        created.chapterTitle = "new chapter"
        val updated = repository.update(created)

        assertThat(updated.noteContent).isEqualTo("edited")
        assertThat(updated.color).isEqualTo("#00FF00")
        assertThat(updated.chapterTitle).isEqualTo("new chapter")
        assertThat(updated.createdAt).isEqualTo(created.createdAt)
        assertThat(dsl.select(BOOK_NOTES_V2.VERSION).from(BOOK_NOTES_V2).fetchOne(BOOK_NOTES_V2.VERSION)).isEqualTo(1L)
    }

    @Test
    fun `list is ordered by created_at desc and scoped by user`() {
        val first = repository.insert(bookId, userId, "a", null, "c", "#FFC107", null)
        val second = repository.insert(bookId, userId, "b", null, "c", "#FFC107", null)
        repository.insert(bookId, otherUserId, "c", null, "c", "#FFC107", null)
        dsl.update(BOOK_NOTES_V2).set(BOOK_NOTES_V2.CREATED_AT, LocalDateTime.of(2026, 1, 1, 0, 0))
            .where(BOOK_NOTES_V2.ID.eq(first.id)).execute()
        dsl.update(BOOK_NOTES_V2).set(BOOK_NOTES_V2.CREATED_AT, LocalDateTime.of(2026, 1, 2, 0, 0))
            .where(BOOK_NOTES_V2.ID.eq(second.id)).execute()

        val list = repository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId)

        assertThat(list.map { it.cfi }).containsExactly("b", "a")
    }

    @Test
    fun `existsByCfi is user and book scoped`() {
        repository.insert(bookId, userId, "dupcfi", null, "c", "#FFC107", null)

        assertThat(repository.existsByCfiAndBookIdAndUserId("dupcfi", bookId, userId)).isTrue()
        assertThat(repository.existsByCfiAndBookIdAndUserId("dupcfi", bookId, otherUserId)).isFalse()
        assertThat(repository.existsByCfiAndBookIdAndUserId("nope", bookId, userId)).isFalse()
    }

    @Test
    fun `findByIdAndUserId enforces ownership, delete removes row`() {
        val n = repository.insert(bookId, userId, "cfi", null, "c", "#FFC107", null)
        assertThat(repository.findByIdAndUserId(n.id, otherUserId)).isNull()

        repository.deleteById(n.id)

        assertThat(repository.findByIdAndUserId(n.id, userId)).isNull()
        assertThat(dsl.fetchCount(BOOK_NOTES_V2)).isZero()
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
