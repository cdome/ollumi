package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookShelfMapping.BOOK_SHELF_MAPPING
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Shelf.SHELF
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

class JooqAppBookRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repository: JooqAppBookRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private var lib1Id = 0L
    private var lib2Id = 0L
    private var book1Id = 0L   // lib1, EPUB file, author=Tolkien, series=LOTR #1, lang=en, scannedOn set
    private var book2Id = 0L   // lib1, EPUB+AUDIOBOOK files, author=Tolkien, series=LOTR #2, lang=en
    private var book3Id = 0L   // lib2, AUDIOBOOK file, author=Orwell, no series, lang=fr
    private var book4Id = 0L   // lib1, no files (should be excluded by hasDigitalFile)
    private var deletedBookId = 0L  // lib1, deleted, has file
    private var shelfId = 0L
    private var userId = 0L

    private val now = LocalDateTime.now()

    @BeforeEach
    fun setUp() {
        // Clean in dependency order
        dsl.deleteFrom(USER_BOOK_PROGRESS).execute()
        dsl.deleteFrom(BOOK_SHELF_MAPPING).execute()
        dsl.deleteFrom(BOOK_METADATA_AUTHOR_MAPPING).execute()
        dsl.deleteFrom(AUTHOR).execute()
        dsl.deleteFrom(BOOK_FILE).execute()
        dsl.deleteFrom(BOOK_METADATA).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(SHELF).execute()
        dsl.deleteFrom(LIBRARY).execute()
        dsl.deleteFrom(USERS).execute()

        // User
        userId = dsl.insertInto(USERS)
            .set(USERS.USERNAME, "testuser")
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, "Test User")
            .returningResult(USERS.ID)
            .fetchOne()!!.get(USERS.ID)!!

        // Libraries
        lib1Id = dsl.insertInto(LIBRARY)
            .set(LIBRARY.NAME, "Fantasy Library")
            .returningResult(LIBRARY.ID)
            .fetchOne()!!.get(LIBRARY.ID)!!

        lib2Id = dsl.insertInto(LIBRARY)
            .set(LIBRARY.NAME, "SciFi Library")
            .returningResult(LIBRARY.ID)
            .fetchOne()!!.get(LIBRARY.ID)!!

        // Books
        book1Id = insertBook(lib1Id, now.minusDays(3), scannedOn = now.minusDays(1))
        book2Id = insertBook(lib1Id, now.minusDays(2))
        book3Id = insertBook(lib2Id, now.minusDays(1))
        book4Id = insertBook(lib1Id, now)  // no files
        deletedBookId = insertBook(lib1Id, now.plusDays(1), deleted = true)

        // Metadata
        insertMetadata(book1Id, "The Hobbit", seriesName = "LOTR", seriesNumber = 1.0, language = "en")
        insertMetadata(book2Id, "The Two Towers", seriesName = "LOTR", seriesNumber = 2.0, language = "en")
        insertMetadata(book3Id, "1984", language = "fr")
        insertMetadata(book4Id, "No File Book", language = "en")
        insertMetadata(deletedBookId, "Deleted Book")

        // Book files
        insertBookFile(book1Id, "EPUB")
        insertBookFile(book2Id, "EPUB")
        insertBookFile(book2Id, "AUDIOBOOK")
        insertBookFile(book3Id, "AUDIOBOOK")
        insertBookFile(deletedBookId, "PDF")

        // Authors
        val tolkienId = insertAuthor("J.R.R. Tolkien")
        val orwellId = insertAuthor("George Orwell")
        insertAuthorMapping(book1Id, tolkienId)
        insertAuthorMapping(book2Id, tolkienId)
        insertAuthorMapping(book3Id, orwellId)

        // Shelf
        shelfId = dsl.insertInto(SHELF)
            .set(SHELF.USER_ID, userId)
            .set(SHELF.NAME, "Favorites")
            .returningResult(SHELF.ID)
            .fetchOne()!!.get(SHELF.ID)!!
        dsl.insertInto(BOOK_SHELF_MAPPING)
            .set(BOOK_SHELF_MAPPING.BOOK_ID, book1Id)
            .set(BOOK_SHELF_MAPPING.SHELF_ID, shelfId)
            .execute()
        dsl.insertInto(BOOK_SHELF_MAPPING)
            .set(BOOK_SHELF_MAPPING.BOOK_ID, book2Id)
            .set(BOOK_SHELF_MAPPING.SHELF_ID, shelfId)
            .execute()

        // User book progress
        insertProgress(userId, book1Id, readStatus = "READ", rating = 5)
        insertProgress(userId, book2Id, readStatus = "READING", rating = 3)
        insertProgress(userId, book3Id, readStatus = "WANT_TO_READ")
    }

    // =============================================================
    // findBookIds — basic pagination, sorting, and condition tests
    // =============================================================

    @Test
    fun `findBookIds with notDeleted returns only non-deleted books`() {
        val condition = AppBookConditions.notDeleted()
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).contains(book1Id, book2Id, book3Id, book4Id)
        assertThat(page.content).doesNotContain(deletedBookId)
    }

    @Test
    fun `findBookIds with hasDigitalFile excludes books without files`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id, book3Id)
        assertThat(page.content).doesNotContain(book4Id, deletedBookId)
    }

    @Test
    fun `findBookIds paginates correctly`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())

        val page1 = repository.findBookIds(condition, PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "addedOn")))
        assertThat(page1.content).hasSize(2)
        assertThat(page1.totalElements).isEqualTo(3)
        assertThat(page1.content).containsExactly(book1Id, book2Id)

        val page2 = repository.findBookIds(condition, PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "addedOn")))
        assertThat(page2.content).hasSize(1)
        assertThat(page2.content).containsExactly(book3Id)
    }

    @Test
    fun `findBookIds sorts by metadata title`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
        val page = repository.findBookIds(condition, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "metadata.title")))

        // 1984, The Hobbit, The Two Towers
        assertThat(page.content).containsExactly(book3Id, book1Id, book2Id)
    }

    @Test
    fun `findBookIds sorts by addedOn descending`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
        val page = repository.findBookIds(condition, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "addedOn")))

        assertThat(page.content).containsExactly(book3Id, book2Id, book1Id)
    }

    // =============================================================
    // Library / shelf filtering
    // =============================================================

    @Test
    fun `inLibrary filters to single library`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
            .and(AppBookConditions.inLibrary(lib1Id))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id)
    }

    @Test
    fun `inLibraries filters to multiple libraries`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
            .and(AppBookConditions.inLibraries(listOf(lib1Id)))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id)
    }

    @Test
    fun `inShelf filters to books on shelf`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
            .and(AppBookConditions.inShelf(shelfId))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id)
    }

    // =============================================================
    // File type conditions
    // =============================================================

    @Test
    fun `hasAudiobookFile returns books with audiobook files`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasAudiobookFile())
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactlyInAnyOrder(book2Id, book3Id)
    }

    @Test
    fun `hasNonAudiobookFile returns books with non-audiobook files`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasNonAudiobookFile())
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id)
    }

    @Test
    fun `withFileType filters by specific file type`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.withFileType("EPUB"))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id)
    }

    // =============================================================
    // Scan / date conditions
    // =============================================================

    @Test
    fun `hasScannedOn returns only scanned books`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasScannedOn())
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactly(book1Id)
    }

    @Test
    fun `addedWithinDays filters by recent additions`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.addedWithinDays(2))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        // book3 (1 day ago), book4 (now) are within 2 days
        assertThat(page.content).contains(book3Id, book4Id)
        assertThat(page.content).doesNotContain(book1Id) // 3 days ago
    }

    // =============================================================
    // Search and author/language/series
    // =============================================================

    @Test
    fun `searchText matches title`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.searchText("hobbit"))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactly(book1Id)
    }

    @Test
    fun `searchText matches series name`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.searchText("lotr"))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id)
    }

    @Test
    fun `searchText matches author name`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.searchText("orwell"))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactly(book3Id)
    }

    @Test
    fun `withAuthor filters by exact author name`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.withAuthor("J.R.R. Tolkien"))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id)
    }

    @Test
    fun `withLanguage filters by language`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.withLanguage("fr"))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactly(book3Id)
    }

    @Test
    fun `inSeries filters by series name`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.inSeries("LOTR"))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "metadata.seriesNumber")))

        assertThat(page.content).containsExactly(book1Id, book2Id)
    }

    // =============================================================
    // Read status and rating conditions
    // =============================================================

    @Test
    fun `withReadStatus filters by status`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.withReadStatus("READ", userId))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactly(book1Id)
    }

    @Test
    fun `inProgress returns reading and re-reading books`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.inProgress(userId))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactly(book2Id)
    }

    @Test
    fun `withMinRating filters by minimum rating`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.withMinRating(4, userId))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactly(book1Id) // rating 5
    }

    @Test
    fun `withMaxRating filters by maximum rating`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.withMaxRating(3, userId))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        assertThat(page.content).containsExactly(book2Id) // rating 3
    }

    @Test
    fun `withMaxRating zero returns unrated books`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
            .and(AppBookConditions.withMaxRating(0, userId))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10))

        // book3 has progress but no rating, so it's "unrated"
        assertThat(page.content).containsExactly(book3Id)
    }

    // =============================================================
    // Condition composition
    // =============================================================

    @Test
    fun `multiple conditions compose correctly`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
            .and(AppBookConditions.inLibrary(lib1Id))
            .and(AppBookConditions.withAuthor("J.R.R. Tolkien"))
            .and(AppBookConditions.inSeries("LOTR"))
        val page = repository.findBookIds(condition, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "metadata.seriesNumber")))

        assertThat(page.content).containsExactly(book1Id, book2Id)
    }

    // =============================================================
    // findAllBookIds (unpaginated)
    // =============================================================

    @Test
    fun `findAllBookIds returns all matching IDs`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
        val ids = repository.findAllBookIds(condition)

        assertThat(ids).containsExactlyInAnyOrder(book1Id, book2Id, book3Id)
    }

    // =============================================================
    // countBooks
    // =============================================================

    @Test
    fun `countBooks returns correct count`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
        val count = repository.countBooks(condition)

        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `countBooks with library filter`() {
        val condition = AppBookConditions.notDeleted()
            .and(AppBookConditions.hasDigitalFile())
            .and(AppBookConditions.inLibrary(lib1Id))
        val count = repository.countBooks(condition)

        assertThat(count).isEqualTo(2)
    }

    // =============================================================
    // Helpers
    // =============================================================

    private fun insertBook(
        libraryId: Long,
        addedOn: LocalDateTime,
        scannedOn: LocalDateTime? = null,
        deleted: Boolean = false
    ): Long {
        val insert = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .set(BOOK.ADDED_ON, addedOn)
        if (scannedOn != null) insert.set(BOOK.SCANNED_ON, scannedOn)
        if (deleted) insert.set(BOOK.DELETED, 1.toByte())
        return insert.returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
    }

    private fun insertMetadata(
        bookId: Long,
        title: String,
        seriesName: String? = null,
        seriesNumber: Double? = null,
        language: String? = null
    ) {
        val insert = dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, bookId)
            .set(BOOK_METADATA.TITLE, title)
        if (seriesName != null) insert.set(BOOK_METADATA.SERIES_NAME, seriesName)
        if (seriesNumber != null) insert.set(BOOK_METADATA.SERIES_NUMBER, seriesNumber)
        if (language != null) insert.set(BOOK_METADATA.LANGUAGE, language)
        insert.execute()
    }

    private fun insertBookFile(bookId: Long, bookType: String) {
        dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, "$bookType-$bookId.file")
            .set(BOOK_FILE.FILE_SUB_PATH, "/files")
            .set(BOOK_FILE.BOOK_TYPE, bookType)
            .execute()
    }

    private fun insertAuthor(name: String): Long =
        dsl.insertInto(AUTHOR)
            .set(AUTHOR.NAME, name)
            .returningResult(AUTHOR.ID)
            .fetchOne()!!.get(AUTHOR.ID)!!

    private fun insertAuthorMapping(bookId: Long, authorId: Long) {
        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, authorId)
            .execute()
    }

    private fun insertProgress(
        userId: Long,
        bookId: Long,
        readStatus: String? = null,
        rating: Int? = null
    ) {
        val insert = dsl.insertInto(USER_BOOK_PROGRESS)
            .set(USER_BOOK_PROGRESS.USER_ID, userId)
            .set(USER_BOOK_PROGRESS.BOOK_ID, bookId)
        if (readStatus != null) insert.set(USER_BOOK_PROGRESS.READ_STATUS, readStatus)
        if (rating != null) insert.set(USER_BOOK_PROGRESS.PERSONAL_RATING, rating.toByte())
        insert.execute()
    }
}
