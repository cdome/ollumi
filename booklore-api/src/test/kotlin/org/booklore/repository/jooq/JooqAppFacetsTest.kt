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

/**
 * Integration coverage for the app filter-option facets, author listing and
 * series listing jOOQ queries introduced when removing EntityManager JPQL.
 */
class JooqAppFacetsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var bookRepository: JooqAppBookRepository
    @Autowired private lateinit var authorRepository: JooqAppAuthorRepository
    @Autowired private lateinit var seriesRepository: JooqAppSeriesRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId = 0L
    private var lib1Id = 0L
    private var lib2Id = 0L
    private var tolkienId = 0L
    private var orwellId = 0L

    private var book1 = 0L
    private var book2 = 0L
    private var book3 = 0L
    private var book4 = 0L
    private var deleted = 0L

    @BeforeEach
    fun setUp() {
        listOf(
            USER_BOOK_PROGRESS, BOOK_METADATA_AUTHOR_MAPPING, AUTHOR,
            BOOK_FILE, BOOK_METADATA, BOOK, LIBRARY, USERS
        ).forEach { dsl.deleteFrom(it).execute() }

        userId = insertUser("alice")
        lib1Id = insertLibrary("Main")
        lib2Id = insertLibrary("Annex")
        tolkienId = insertAuthor("J.R.R. Tolkien")
        orwellId = insertAuthor("George Orwell")

        val now = LocalDateTime.now()

        book1 = insertBook(lib1Id, now.minusDays(3))
        insertMetadata(book1, "Book One", "en", "Alpha", 1.0, null)
        insertFile(book1, "EPUB")
        linkAuthor(book1, tolkienId); linkAuthor(book1, orwellId)
        insertProgress(book1, "READ", now.minusDays(1))

        book2 = insertBook(lib1Id, now.minusDays(2))
        insertMetadata(book2, "Book Two", "en", "Alpha", 2.0, null)
        insertFile(book2, "EPUB"); insertFile(book2, "AUDIOBOOK")
        linkAuthor(book2, tolkienId)
        insertProgress(book2, "READING", now)

        book3 = insertBook(lib2Id, now.minusDays(1))
        insertMetadata(book3, "Book Three", "fr", "Beta", 1.0, 1)
        insertFile(book3, "PDF")
        linkAuthor(book3, orwellId)

        book4 = insertBook(lib1Id, now)
        insertMetadata(book4, "Loner", "en", null, null, null)
        insertFile(book4, "EPUB")
        linkAuthor(book4, tolkienId)

        deleted = insertBook(lib1Id, now, isDeleted = true)
        insertMetadata(deleted, "Ghost", "en", "Alpha", 3.0, null)
        insertFile(deleted, "EPUB")
        linkAuthor(deleted, tolkienId)
    }

    // ---- Filter-option facets (scope built from AppBookConditions) ----

    private fun scope() = AppBookConditions.notDeleted().and(AppBookConditions.hasDigitalFile())

    @Test
    fun `author facets are counted and ordered by book count`() {
        val facets = bookRepository.findAuthorFacets(scope(), 200)
        assertThat(facets.map { it.name to it.count })
            .containsExactly("J.R.R. Tolkien" to 3L, "George Orwell" to 2L)
    }

    @Test
    fun `language facets exclude deleted books`() {
        val facets = bookRepository.findLanguageFacets(scope())
        assertThat(facets.map { it.code to it.count })
            .containsExactly("en" to 3L, "fr" to 1L)
    }

    @Test
    fun `file types are distinct book-format types in scope`() {
        assertThat(bookRepository.findFileTypes(scope()).sorted())
            .containsExactly("AUDIOBOOK", "EPUB", "PDF")
    }

    @Test
    fun `facets honor a library scope`() {
        val scoped = scope().and(AppBookConditions.inLibrary(lib2Id))
        assertThat(bookRepository.findAuthorFacets(scoped, 200).map { it.name })
            .containsExactly("George Orwell")
        assertThat(bookRepository.findFileTypes(scoped)).containsExactly("PDF")
    }

    // ---- Author listing ----

    @Test
    fun `countAuthors counts authors with accessible non-deleted books`() {
        assertThat(authorRepository.countAuthors(null, null, null)).isEqualTo(2)
        assertThat(authorRepository.countAuthors(null, lib2Id, null)).isEqualTo(1)
        assertThat(authorRepository.countAuthors(emptySet(), null, null)).isZero()
    }

    @Test
    fun `author summaries carry per-author book counts and sort`() {
        val byName = authorRepository.findAuthorSummaries(null, null, null, "name", "asc", 0, 30)
        assertThat(byName.map { it.name }).containsExactly("George Orwell", "J.R.R. Tolkien")

        val byCount = authorRepository.findAuthorSummaries(null, null, null, "bookCount", "desc", 0, 30)
        assertThat(byCount.map { it.name to it.bookCount })
            .containsExactly("J.R.R. Tolkien" to 3L, "George Orwell" to 2L)
    }

    @Test
    fun `author search filters by name`() {
        val rows = authorRepository.findAuthorSummaries(null, null, "orwell", "name", "asc", 0, 30)
        assertThat(rows.map { it.name }).containsExactly("George Orwell")
    }

    @Test
    fun `countAccessibleBooks counts an author's non-deleted books`() {
        assertThat(authorRepository.countAccessibleBooks(tolkienId, null)).isEqualTo(3)
        assertThat(authorRepository.countAccessibleBooks(orwellId, setOf(lib1Id))).isEqualTo(1)
    }

    @Test
    fun `matching author ids respect the filter`() {
        assertThat(authorRepository.findMatchingAuthorIds(null, null, null))
            .containsExactlyInAnyOrder(tolkienId, orwellId)
        assertThat(authorRepository.findMatchingAuthorIds(null, lib2Id, null))
            .containsExactly(orwellId)
    }

    // ---- Series listing ----

    @Test
    fun `series aggregates group counts, totals and read counts`() {
        val rows = seriesRepository.findSeriesAggregates(
            userId, null, null, null, false, "name", "asc", 0, 30)

        assertThat(rows.map { it.seriesName }).containsExactly("Alpha", "Beta")
        val alpha = rows.first { it.seriesName == "Alpha" }
        assertThat(alpha.bookCount).isEqualTo(2)   // deleted #3 excluded
        assertThat(alpha.booksRead).isEqualTo(1)   // book1 READ, book2 READING
        assertThat(alpha.seriesTotal).isNull()
        val beta = rows.first { it.seriesName == "Beta" }
        assertThat(beta.bookCount).isEqualTo(1)
        assertThat(beta.seriesTotal).isEqualTo(1)
        assertThat(beta.booksRead).isZero()
    }

    @Test
    fun `series count matches aggregate size`() {
        assertThat(seriesRepository.countSeries(userId, null, null, null, false)).isEqualTo(2)
    }

    @Test
    fun `in-progress-only keeps series with a reading book`() {
        val rows = seriesRepository.findSeriesAggregates(
            userId, null, null, null, true, "name", "asc", 0, 30)
        assertThat(rows.map { it.seriesName }).containsExactly("Alpha")
        assertThat(seriesRepository.countSeries(userId, null, null, null, true)).isEqualTo(1)
    }

    @Test
    fun `series aggregates honor a library scope`() {
        val rows = seriesRepository.findSeriesAggregates(
            userId, null, lib2Id, null, false, "name", "asc", 0, 30)
        assertThat(rows.map { it.seriesName }).containsExactly("Beta")
    }

    @Test
    fun `series search filters by series name`() {
        val rows = seriesRepository.findSeriesAggregates(
            userId, null, null, "alph", false, "name", "asc", 0, 30)
        assertThat(rows.map { it.seriesName }).containsExactly("Alpha")
    }

    @Test
    fun `book ids by series names exclude deleted books and other libraries`() {
        assertThat(seriesRepository.findBookIdsBySeriesNames(listOf("Alpha"), null, null))
            .containsExactlyInAnyOrder(book1, book2)
        assertThat(seriesRepository.findBookIdsBySeriesNames(listOf("Beta"), null, lib1Id))
            .isEmpty()
        assertThat(seriesRepository.findBookIdsBySeriesNames(emptyList(), null, null)).isEmpty()
    }

    // ---- Fixtures ----

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!

    private fun insertLibrary(name: String): Long =
        dsl.insertInto(LIBRARY).set(LIBRARY.NAME, name)
            .returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!

    private fun insertAuthor(name: String): Long =
        dsl.insertInto(AUTHOR).set(AUTHOR.NAME, name)
            .returningResult(AUTHOR.ID).fetchOne()!!.get(AUTHOR.ID)!!

    private fun insertBook(libraryId: Long, addedOn: LocalDateTime, isDeleted: Boolean = false): Long {
        val insert = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .set(BOOK.ADDED_ON, addedOn)
        if (isDeleted) insert.set(BOOK.DELETED, 1.toByte())
        return insert.returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
    }

    private fun insertMetadata(
        bookId: Long, title: String, language: String?,
        seriesName: String?, seriesNumber: Double?, seriesTotal: Int?
    ) {
        val insert = dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, bookId)
            .set(BOOK_METADATA.TITLE, title)
        if (language != null) insert.set(BOOK_METADATA.LANGUAGE, language)
        if (seriesName != null) insert.set(BOOK_METADATA.SERIES_NAME, seriesName)
        if (seriesNumber != null) insert.set(BOOK_METADATA.SERIES_NUMBER, seriesNumber)
        if (seriesTotal != null) insert.set(BOOK_METADATA.SERIES_TOTAL, seriesTotal)
        insert.execute()
    }

    private fun insertFile(bookId: Long, bookType: String) {
        dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, "$bookType-$bookId.file")
            .set(BOOK_FILE.FILE_SUB_PATH, "/files")
            .set(BOOK_FILE.BOOK_TYPE, bookType)
            .set(BOOK_FILE.IS_BOOK, 1.toByte())
            .execute()
    }

    private fun linkAuthor(bookId: Long, authorId: Long) {
        // sort_order is part of the PK since V129; keep it distinct per book.
        val nextOrder = dsl.fetchCount(BOOK_METADATA_AUTHOR_MAPPING, BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID.eq(bookId))
        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, authorId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.SORT_ORDER, nextOrder)
            .execute()
    }

    private fun insertProgress(bookId: Long, readStatus: String, lastReadTime: LocalDateTime) {
        dsl.insertInto(USER_BOOK_PROGRESS)
            .set(USER_BOOK_PROGRESS.USER_ID, userId)
            .set(USER_BOOK_PROGRESS.BOOK_ID, bookId)
            .set(USER_BOOK_PROGRESS.READ_STATUS, readStatus)
            .set(USER_BOOK_PROGRESS.LAST_READ_TIME, lastReadTime)
            .execute()
    }
}
