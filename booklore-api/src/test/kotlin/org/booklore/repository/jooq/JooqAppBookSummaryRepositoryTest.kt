package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.time.ZoneOffset

class JooqAppBookSummaryRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqAppBookSummaryRepository
    @Autowired private lateinit var dsl: DSLContext

    private var user1 = 0L
    private var user2 = 0L
    private var lib1 = 0L   // format_priority ["PDF","EPUB"]
    private var lib2 = 0L   // no format priority
    private var book1 = 0L
    private var book2 = 0L
    private var book3 = 0L

    private val cover = LocalDateTime.of(2026, 2, 1, 8, 0)
    private val audioCover = LocalDateTime.of(2026, 2, 2, 9, 0)
    private val lastRead = LocalDateTime.of(2026, 3, 3, 10, 0)

    @BeforeEach
    fun setUp() {
        listOf(
            USER_BOOK_PROGRESS, BOOK_METADATA_AUTHOR_MAPPING, AUTHOR,
            BOOK_FILE, BOOK_METADATA, BOOK, LIBRARY, USERS
        ).forEach { dsl.deleteFrom(it).execute() }

        user1 = insertUser("alice")
        user2 = insertUser("bob")
        lib1 = insertLibrary("Priority", """["PDF","EPUB"]""")
        lib2 = insertLibrary("Plain", null)

        // book1: two authors (ordered), EPUB then PDF files; library prefers PDF.
        book1 = insertBook(lib1, LocalDateTime.of(2026, 1, 10, 0, 0), isPhysical = false)
        insertMetadata(book1, "The Hobbit", "LOTR", 1.5, cover, audioCover)
        linkAuthor(book1, insertAuthor("Tolkien"), 0)
        linkAuthor(book1, insertAuthor("Orwell"), 1)
        insertFile(book1, "hobbit.epub", "EPUB", isBook = true)
        insertFile(book1, "hobbit.pdf", "PDF", isBook = true)
        insertProgress(book1, user1, readStatus = "READ", rating = 5, epubPercent = 80.0, lastReadTime = lastRead)

        // book2: no library priority, AUDIOBOOK inserted first → primary is AUDIOBOOK.
        book2 = insertBook(lib2, LocalDateTime.of(2026, 1, 20, 0, 0), isPhysical = true)
        insertMetadata(book2, "Dune", null, null, null, null)
        linkAuthor(book2, insertAuthor("Herbert"), 0)
        insertFile(book2, "dune.m4b", "AUDIOBOOK", isBook = true)
        insertFile(book2, "dune.epub", "EPUB", isBook = true)
        insertProgress(book2, user1, readStatus = "READING", koreaderPercent = 30.0)

        // book3: no files, no authors, no progress.
        book3 = insertBook(lib2, LocalDateTime.of(2026, 1, 30, 0, 0), isPhysical = false)
        insertMetadata(book3, "Orphan", null, null, null, null)
    }

    @Test
    fun `projects all summary fields for a fully-populated book`() {
        val summary = repository.findSummariesByIds(listOf(book1), user1).single()

        assertThat(summary.id).isEqualTo(book1)
        assertThat(summary.title).isEqualTo("The Hobbit")
        assertThat(summary.authors).containsExactly("Tolkien", "Orwell")
        assertThat(summary.thumbnailUrl).isEqualTo("/api/books/$book1/cover")
        assertThat(summary.readStatus).isEqualTo("READ")
        assertThat(summary.personalRating).isEqualTo(5)
        assertThat(summary.seriesName).isEqualTo("LOTR")
        assertThat(summary.seriesNumber).isEqualTo(1.5f)
        assertThat(summary.libraryId).isEqualTo(lib1)
        assertThat(summary.addedOn).isEqualTo(LocalDateTime.of(2026, 1, 10, 0, 0).toInstant(ZoneOffset.UTC))
        assertThat(summary.lastReadTime).isEqualTo(lastRead.toInstant(ZoneOffset.UTC))
        assertThat(summary.readProgress).isEqualTo(80.0f)
        assertThat(summary.primaryFileType).isEqualTo("PDF")   // format priority beats file order
        assertThat(summary.coverUpdatedOn).isEqualTo(cover.toInstant(ZoneOffset.UTC))
        assertThat(summary.audiobookCoverUpdatedOn).isEqualTo(audioCover.toInstant(ZoneOffset.UTC))
        assertThat(summary.isPhysical).isFalse()
    }

    @Test
    fun `primary file falls back to first file when no format priority`() {
        val summary = repository.findSummariesByIds(listOf(book2), user1).single()
        assertThat(summary.primaryFileType).isEqualTo("AUDIOBOOK")   // first by id, no priority
        assertThat(summary.isPhysical).isTrue()
        assertThat(summary.authors).containsExactly("Herbert")
    }

    @Test
    fun `read progress coalesces koreader before other sources`() {
        val summary = repository.findSummariesByIds(listOf(book2), user1).single()
        // koreader=30 present, so it wins even though epub etc. are null
        assertThat(summary.readProgress).isEqualTo(30.0f)
        assertThat(summary.readStatus).isEqualTo("READING")
        assertThat(summary.personalRating as Int?).isNull()
    }

    @Test
    fun `book without files, authors or progress yields nulls and empty lists`() {
        val summary = repository.findSummariesByIds(listOf(book3), user1).single()
        assertThat(summary.primaryFileType).isNull()
        assertThat(summary.authors).isEmpty()
        assertThat(summary.readStatus).isNull()
        assertThat(summary.readProgress as Float?).isNull()
        assertThat(summary.personalRating as Int?).isNull()
    }

    @Test
    fun `progress is scoped to the requested user`() {
        val forOther = repository.findSummariesByIds(listOf(book1), user2).single()
        assertThat(forOther.readStatus).isNull()
        assertThat(forOther.personalRating as Int?).isNull()
        assertThat(forOther.readProgress as Float?).isNull()
        // non-progress fields still populated
        assertThat(forOther.title).isEqualTo("The Hobbit")
        assertThat(forOther.primaryFileType).isEqualTo("PDF")
    }

    @Test
    fun `returns a row per requested id and empty for empty input`() {
        val all = repository.findSummariesByIds(listOf(book1, book2, book3), user1)
        assertThat(all).extracting<Long> { it.id }.containsExactlyInAnyOrder(book1, book2, book3)
        assertThat(repository.findSummariesByIds(emptyList(), user1)).isEmpty()
    }

    // ---- Fixtures ----

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username).set(USERS.PASSWORD_HASH, "h")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte()).set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!

    private fun insertLibrary(name: String, formatPriority: String?): Long {
        val insert = dsl.insertInto(LIBRARY).set(LIBRARY.NAME, name)
        if (formatPriority != null) insert.set(LIBRARY.FORMAT_PRIORITY, formatPriority)
        return insert.returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!
    }

    private fun insertAuthor(name: String): Long =
        dsl.insertInto(AUTHOR).set(AUTHOR.NAME, name)
            .returningResult(AUTHOR.ID).fetchOne()!!.get(AUTHOR.ID)!!

    private fun insertBook(libraryId: Long, addedOn: LocalDateTime, isPhysical: Boolean): Long =
        dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .set(BOOK.ADDED_ON, addedOn)
            .set(BOOK.IS_PHYSICAL, if (isPhysical) 1.toByte() else 0.toByte())
            .returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!

    private fun insertMetadata(
        bookId: Long, title: String, seriesName: String?, seriesNumber: Double?,
        coverUpdatedOn: LocalDateTime?, audiobookCoverUpdatedOn: LocalDateTime?
    ) {
        val insert = dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, bookId).set(BOOK_METADATA.TITLE, title)
        if (seriesName != null) insert.set(BOOK_METADATA.SERIES_NAME, seriesName)
        if (seriesNumber != null) insert.set(BOOK_METADATA.SERIES_NUMBER, seriesNumber)
        if (coverUpdatedOn != null) insert.set(BOOK_METADATA.COVER_UPDATED_ON, coverUpdatedOn)
        if (audiobookCoverUpdatedOn != null) insert.set(BOOK_METADATA.AUDIOBOOK_COVER_UPDATED_ON, audiobookCoverUpdatedOn)
        insert.execute()
    }

    private fun linkAuthor(bookId: Long, authorId: Long, sortOrder: Int) {
        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, authorId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.SORT_ORDER, sortOrder)
            .execute()
    }

    private fun insertFile(bookId: Long, fileName: String, bookType: String, isBook: Boolean) {
        dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, fileName)
            .set(BOOK_FILE.FILE_SUB_PATH, "/files")
            .set(BOOK_FILE.BOOK_TYPE, bookType)
            .set(BOOK_FILE.IS_BOOK, if (isBook) 1.toByte() else 0.toByte())
            .execute()
    }

    private fun insertProgress(
        bookId: Long, userId: Long, readStatus: String? = null, rating: Int? = null,
        epubPercent: Double? = null, koreaderPercent: Double? = null, lastReadTime: LocalDateTime? = null
    ) {
        val insert = dsl.insertInto(USER_BOOK_PROGRESS)
            .set(USER_BOOK_PROGRESS.USER_ID, userId)
            .set(USER_BOOK_PROGRESS.BOOK_ID, bookId)
        if (readStatus != null) insert.set(USER_BOOK_PROGRESS.READ_STATUS, readStatus)
        if (rating != null) insert.set(USER_BOOK_PROGRESS.PERSONAL_RATING, rating.toByte())
        if (epubPercent != null) insert.set(USER_BOOK_PROGRESS.EPUB_PROGRESS_PERCENT, epubPercent)
        if (koreaderPercent != null) insert.set(USER_BOOK_PROGRESS.KOREADER_PROGRESS_PERCENT, koreaderPercent)
        if (lastReadTime != null) insert.set(USER_BOOK_PROGRESS.LAST_READ_TIME, lastReadTime)
        insert.execute()
    }
}
