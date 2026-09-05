package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.ReadingSessions.READING_SESSIONS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.enums.BookFileType
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class JooqReadingSessionRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqReadingSessionRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var bookId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(READING_SESSIONS).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY).execute()
        dsl.deleteFrom(USERS).execute()

        userId = insertUser("reader")
        val libId = insertLibrary("lib")
        bookId = insertBook(libId)
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username).set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte()).set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!

    private fun insertLibrary(name: String): Long =
        dsl.insertInto(LIBRARY).set(LIBRARY.NAME, name)
            .returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!

    private fun insertBook(libraryId: Long): Long =
        dsl.insertInto(BOOK).set(BOOK.LIBRARY_ID, libraryId)
            .returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!

    @Test
    fun `insert persists a session and returns its id`() {
        val start = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)
        val end = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        val id = repository.insert(
            userId, bookId, BookFileType.EPUB, start, end,
            3600, "1h", 10.0f, 55.5f, 45.5f, "cfi/start", "cfi/end",
        )
        assertThat(id).isNotNull()

        val page = repository.findByUserIdAndBookId(userId, bookId, PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(1)
        val detail = page.content[0]
        assertThat(detail.id).isEqualTo(id)
        assertThat(detail.bookId).isEqualTo(bookId)
        assertThat(detail.bookType).isEqualTo("EPUB")
        assertThat(detail.durationSeconds).isEqualTo(3600)
        assertThat(detail.startProgress).isEqualTo(10.0)
        assertThat(detail.endProgress).isEqualTo(55.5)
        assertThat(detail.progressDelta).isEqualTo(45.5)
        assertThat(detail.startLocation).isEqualTo("cfi/start")
        assertThat(detail.endLocation).isEqualTo("cfi/end")
        assertThat(detail.createdAt).isNotNull()
        // start/end are stored as UTC LocalDateTime (matches Hibernate jdbc.time_zone=UTC)
        assertThat(detail.startTime).isEqualTo(LocalDateTime.ofInstant(start, ZoneOffset.UTC))
        assertThat(detail.endTime).isEqualTo(LocalDateTime.ofInstant(end, ZoneOffset.UTC))
    }

    @Test
    fun `insert allows null progress and location fields`() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        repository.insert(userId, bookId, BookFileType.AUDIOBOOK, now, now, 120, null, null, null, null, null, null)

        val detail = repository.findByUserIdAndBookId(userId, bookId, PageRequest.of(0, 10)).content[0]
        assertThat(detail.startProgress as Double?).isNull()
        assertThat(detail.endProgress as Double?).isNull()
        assertThat(detail.progressDelta as Double?).isNull()
        assertThat(detail.startLocation).isNull()
        assertThat(detail.endLocation).isNull()
    }

    @Test
    fun `deleting the owning user cascades to reading_sessions`() {
        repository.insert(userId, bookId, BookFileType.EPUB, Instant.now(), Instant.now(), 60, null, null, null, null, null, null)
        assertThat(dsl.fetchCount(READING_SESSIONS)).isEqualTo(1)

        dsl.deleteFrom(USERS).where(USERS.ID.eq(userId)).execute()

        assertThat(dsl.fetchCount(READING_SESSIONS)).isZero() // fk_reading_session_user ON DELETE CASCADE (V78)
    }

    @Test
    fun `deleting the book cascades to reading_sessions`() {
        repository.insert(userId, bookId, BookFileType.EPUB, Instant.now(), Instant.now(), 60, null, null, null, null, null, null)
        assertThat(dsl.fetchCount(READING_SESSIONS)).isEqualTo(1)

        dsl.deleteFrom(BOOK).where(BOOK.ID.eq(bookId)).execute()

        assertThat(dsl.fetchCount(READING_SESSIONS)).isZero() // fk_reading_session_book ON DELETE CASCADE (V78)
    }
}
