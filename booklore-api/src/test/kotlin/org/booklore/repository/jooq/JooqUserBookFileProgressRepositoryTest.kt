package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.UserBookFileProgress.USER_BOOK_FILE_PROGRESS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.enums.BookFileType
import org.booklore.repository.jooq.dto.UserBookFileProgressRow
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

class JooqUserBookFileProgressRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqUserBookFileProgressRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var bookId1: Long = 0
    private var bookId2: Long = 0
    private var epubFileId: Long = 0
    private var audioFileId: Long = 0
    private var book2FileId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(USER_BOOK_FILE_PROGRESS).execute()
        dsl.deleteFrom(BOOK_FILE).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY).execute()
        dsl.deleteFrom(USERS).execute()

        userId = insertUser("reader")
        val libId = insertLibrary("lib")
        bookId1 = insertBook(libId)
        bookId2 = insertBook(libId)
        epubFileId = insertBookFile(bookId1, "a.epub", BookFileType.EPUB)
        audioFileId = insertBookFile(bookId1, "a.m4b", BookFileType.AUDIOBOOK)
        book2FileId = insertBookFile(bookId2, "b.epub", BookFileType.EPUB)
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

    private fun insertBookFile(bookId: Long, fileName: String, type: BookFileType): Long =
        dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, fileName)
            .set(BOOK_FILE.FILE_SUB_PATH, "/files")
            .set(BOOK_FILE.BOOK_TYPE, type.name)
            .returningResult(BOOK_FILE.ID).fetchOne()!!.get(BOOK_FILE.ID)!!

    @Test
    fun `save inserts a new row then findByUserIdAndBookFileId returns it with joined bookId and bookType`() {
        val now = Instant.now()
        val saved = repository.save(UserBookFileProgressRow().apply {
            this.userId = this@JooqUserBookFileProgressRepositoryTest.userId
            bookFileId = epubFileId
            positionData = "cfi/6/4"
            positionHref = "chapter1.html"
            progressPercent = 42.5f
            ttsPositionCfi = "cfi/tts"
            lastReadTime = now
        })
        assertThat(saved.id).isNotNull()

        val found = repository.findByUserIdAndBookFileId(userId, epubFileId)
        assertThat(found).isPresent
        val r = found.get()
        assertThat(r.bookFileId).isEqualTo(epubFileId)
        assertThat(r.bookId).isEqualTo(bookId1)              // joined from book_file
        assertThat(r.bookType).isEqualTo(BookFileType.EPUB)  // joined from book_file
        assertThat(r.positionData).isEqualTo("cfi/6/4")
        assertThat(r.positionHref).isEqualTo("chapter1.html")
        assertThat(r.progressPercent).isEqualTo(42.5f)
        assertThat(r.ttsPositionCfi).isEqualTo("cfi/tts")
        assertThat(r.lastReadTime).isNotNull()
    }

    @Test
    fun `save updates in place when the row has an id and preserves unset columns`() {
        val row = repository.save(UserBookFileProgressRow().apply {
            userId = this@JooqUserBookFileProgressRepositoryTest.userId
            bookFileId = epubFileId
            positionHref = "keep-me"
            progressPercent = 10f
            lastReadTime = Instant.now()
        })

        // load, mutate a subset, save -> update by id
        val loaded = repository.findByUserIdAndBookFileId(userId, epubFileId).get()
        loaded.progressPercent = 80f
        loaded.lastReadTime = Instant.now().plusSeconds(60)
        repository.save(loaded)

        val after = repository.findByUserIdAndBookFileId(userId, epubFileId).get()
        assertThat(after.id).isEqualTo(row.id)                 // same row (no duplicate)
        assertThat(after.progressPercent).isEqualTo(80f)
        assertThat(after.positionHref).isEqualTo("keep-me")    // preserved (rewritten from the loaded row)
        assertThat(dsl.fetchCount(USER_BOOK_FILE_PROGRESS)).isEqualTo(1)
    }

    @Test
    fun `findByUserIdAndBookFileId is empty for unknown pair`() {
        assertThat(repository.findByUserIdAndBookFileId(userId, 999_999L)).isEmpty
    }

    @Test
    fun `findByUserIdAndBookFileBookIdIn returns rows across books with projections`() {
        repository.save(UserBookFileProgressRow().apply { userId = this@JooqUserBookFileProgressRepositoryTest.userId; bookFileId = epubFileId; progressPercent = 1f; lastReadTime = Instant.now() })
        repository.save(UserBookFileProgressRow().apply { userId = this@JooqUserBookFileProgressRepositoryTest.userId; bookFileId = book2FileId; progressPercent = 2f; lastReadTime = Instant.now() })

        val rows = repository.findByUserIdAndBookFileBookIdIn(userId, listOf(bookId1, bookId2))
        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.bookId }).containsExactlyInAnyOrder(bookId1, bookId2)
        assertThat(rows.map { it.bookType }).containsOnly(BookFileType.EPUB)
        assertThat(repository.findByUserIdAndBookFileBookIdIn(userId, emptyList())).isEmpty()
    }

    @Test
    fun `findByUserIdAndBookFileBookIdIn projects audiobook type`() {
        repository.save(UserBookFileProgressRow().apply { userId = this@JooqUserBookFileProgressRepositoryTest.userId; bookFileId = audioFileId; progressPercent = 5f; lastReadTime = Instant.now() })
        val rows = repository.findByUserIdAndBookFileBookIdIn(userId, listOf(bookId1))
        assertThat(rows).hasSize(1)
        assertThat(rows[0].bookType).isEqualTo(BookFileType.AUDIOBOOK)
    }

    @Test
    fun `deleteByUserIdAndBookIds deletes all files of the given books for the user`() {
        repository.save(UserBookFileProgressRow().apply { userId = this@JooqUserBookFileProgressRepositoryTest.userId; bookFileId = epubFileId; lastReadTime = Instant.now() })
        repository.save(UserBookFileProgressRow().apply { userId = this@JooqUserBookFileProgressRepositoryTest.userId; bookFileId = audioFileId; lastReadTime = Instant.now() })
        repository.save(UserBookFileProgressRow().apply { userId = this@JooqUserBookFileProgressRepositoryTest.userId; bookFileId = book2FileId; lastReadTime = Instant.now() })

        val deleted = repository.deleteByUserIdAndBookIds(userId, listOf(bookId1))
        assertThat(deleted).isEqualTo(2) // both files of book1
        assertThat(repository.findByUserIdAndBookFileBookIdIn(userId, listOf(bookId1))).isEmpty()
        assertThat(repository.findByUserIdAndBookFileBookIdIn(userId, listOf(bookId2))).hasSize(1)
        assertThat(repository.deleteByUserIdAndBookIds(userId, emptyList())).isZero()
    }
}
