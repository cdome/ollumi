package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookNotes.BOOK_NOTES
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class JooqBookNoteRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqBookNoteRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var otherUserId: Long = 0
    private var bookId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(BOOK_NOTES).execute()
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
    fun `insert returns the persisted note with timestamps`() {
        val note = repository.insert(bookId, userId, "Title", "Content")

        assertThat(note.id).isPositive()
        assertThat(note.bookId).isEqualTo(bookId)
        assertThat(note.userId).isEqualTo(userId)
        assertThat(note.title).isEqualTo("Title")
        assertThat(note.content).isEqualTo("Content")
        assertThat(note.createdAt).isNotNull()
        assertThat(note.updatedAt).isNotNull()
    }

    @Test
    fun `insert allows null title`() {
        val note = repository.insert(bookId, userId, null, "Content")
        assertThat(note.title).isNull()
    }

    @Test
    fun `update changes fields and preserves created_at`() {
        val created = repository.insert(bookId, userId, "old", "old body")

        val updated = repository.update(created.id, userId, "new", "new body")

        assertThat(updated.title).isEqualTo("new")
        assertThat(updated.content).isEqualTo("new body")
        assertThat(updated.createdAt).isEqualTo(created.createdAt)
        assertThat(dsl.fetchCount(BOOK_NOTES)).isEqualTo(1)
    }

    @Test
    fun `findByBookIdAndUserId orders by updatedAt desc and scopes by user`() {
        val first = repository.insert(bookId, userId, "first", "b")
        val second = repository.insert(bookId, userId, "second", "b")
        repository.insert(bookId, otherUserId, "other", "b")
        // make `first` the most recently updated
        repository.update(first.id, userId, "first", "b2")

        val notes = repository.findByBookIdAndUserIdOrderByUpdatedAtDesc(bookId, userId)

        assertThat(notes).extracting("id").containsExactly(first.id, second.id)
    }

    @Test
    fun `findByIdAndUserId enforces ownership`() {
        val note = repository.insert(bookId, userId, "t", "c")
        assertThat(repository.findByIdAndUserId(note.id, userId)).isNotNull()
        assertThat(repository.findByIdAndUserId(note.id, otherUserId)).isNull()
    }

    @Test
    fun `deleteById removes the note and count reflects it`() {
        val note = repository.insert(bookId, userId, "t", "c")
        assertThat(repository.count()).isEqualTo(1L)

        repository.deleteById(note.id)

        assertThat(repository.findByIdAndUserId(note.id, userId)).isNull()
        assertThat(repository.count()).isZero()
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
