package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookMarks.BOOK_MARKS
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class JooqBookMarkRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqBookMarkRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var otherUserId: Long = 0
    private var bookId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(BOOK_MARKS).execute()
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
    fun `insert returns persisted epub bookmark`() {
        val bm = repository.insert(bookId, userId, "epubcfi(/6/4)", null, null, "Chapter 1", 3)

        assertThat(bm.id).isPositive()
        assertThat(bm.bookId).isEqualTo(bookId)
        assertThat(bm.userId).isEqualTo(userId)
        assertThat(bm.cfi).isEqualTo("epubcfi(/6/4)")
        assertThat(bm.positionMs as Long?).isNull()
        assertThat(bm.trackIndex as Int?).isNull()
        assertThat(bm.title).isEqualTo("Chapter 1")
        assertThat(bm.priority).isEqualTo(3)
        assertThat(bm.createdAt).isNotNull()
    }

    @Test
    fun `update writes mutable fields and bumps version`() {
        val created = repository.insert(bookId, userId, "cfi", null, null, "t", 3)
        assertThat(dsl.select(BOOK_MARKS.VERSION).from(BOOK_MARKS).fetchOne(BOOK_MARKS.VERSION)).isEqualTo(0L)

        created.title = "new title"
        created.color = "#FF0000"
        created.notes = "some notes"
        created.priority = 1
        val updated = repository.update(created)

        assertThat(updated.title).isEqualTo("new title")
        assertThat(updated.color).isEqualTo("#FF0000")
        assertThat(updated.notes).isEqualTo("some notes")
        assertThat(updated.priority).isEqualTo(1)
        assertThat(updated.createdAt).isEqualTo(created.createdAt)
        assertThat(dsl.select(BOOK_MARKS.VERSION).from(BOOK_MARKS).fetchOne(BOOK_MARKS.VERSION)).isEqualTo(1L)
    }

    @Test
    fun `list orders by priority asc then created_at desc`() {
        // priority 2, older
        repository.insert(bookId, userId, "a", null, null, "low-prio", 2)
        // two priority 1 — later-created should come first
        val p1old = repository.insert(bookId, userId, "b", null, null, "p1-old", 1)
        val p1new = repository.insert(bookId, userId, "c", null, null, "p1-new", 1)
        // force distinct created_at ordering
        dsl.update(BOOK_MARKS).set(BOOK_MARKS.CREATED_AT, LocalDateTime.of(2026, 1, 1, 0, 0))
            .where(BOOK_MARKS.ID.eq(p1old.id)).execute()
        dsl.update(BOOK_MARKS).set(BOOK_MARKS.CREATED_AT, LocalDateTime.of(2026, 1, 2, 0, 0))
            .where(BOOK_MARKS.ID.eq(p1new.id)).execute()

        val list = repository.findByBookIdAndUserIdOrderByPriorityAscCreatedAtDesc(bookId, userId)

        assertThat(list.map { it.title }).containsExactly("p1-new", "p1-old", "low-prio")
    }

    @Test
    fun `existsByCfi respects excludeId and user scoping`() {
        val bm = repository.insert(bookId, userId, "dupcfi", null, null, "t", 3)

        assertThat(repository.existsByCfiAndBookIdAndUserId("dupcfi", bookId, userId)).isTrue()
        // excluding the only match makes it not-exist (used by update to allow self)
        assertThat(repository.existsByCfiAndBookIdAndUserId("dupcfi", bookId, userId, bm.id)).isFalse()
        // different user cannot see it
        assertThat(repository.existsByCfiAndBookIdAndUserId("dupcfi", bookId, otherUserId)).isFalse()
    }

    @Test
    fun `existsByPositionMsNear matches within 5 seconds and same track`() {
        repository.insert(bookId, userId, null, 10_000L, 2, "audio", 3)

        assertThat(repository.existsByPositionMsNearAndBookIdAndUserId(12_000L, 2, bookId, userId)).isTrue()
        assertThat(repository.existsByPositionMsNearAndBookIdAndUserId(20_000L, 2, bookId, userId)).isFalse()
        // different track index -> not a duplicate
        assertThat(repository.existsByPositionMsNearAndBookIdAndUserId(10_500L, 3, bookId, userId)).isFalse()
    }

    @Test
    fun `existsByPositionMsNear treats null track as its own bucket`() {
        repository.insert(bookId, userId, null, 10_000L, null, "audio", 3)
        assertThat(repository.existsByPositionMsNearAndBookIdAndUserId(11_000L, null, bookId, userId)).isTrue()
        assertThat(repository.existsByPositionMsNearAndBookIdAndUserId(11_000L, 1, bookId, userId)).isFalse()
    }

    @Test
    fun `findByIdAndUserId enforces ownership, delete removes row`() {
        val bm = repository.insert(bookId, userId, "cfi", null, null, "t", 3)
        assertThat(repository.findByIdAndUserId(bm.id, otherUserId)).isNull()
        assertThat(repository.count()).isEqualTo(1L)

        repository.deleteById(bm.id)

        assertThat(repository.findByIdAndUserId(bm.id, userId)).isNull()
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
