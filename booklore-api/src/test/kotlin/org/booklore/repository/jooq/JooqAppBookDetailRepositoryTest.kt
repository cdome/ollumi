package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookMetadataCategoryMapping.BOOK_METADATA_CATEGORY_MAPPING
import org.booklore.jooq.tables.BookShelfMapping.BOOK_SHELF_MAPPING
import org.booklore.jooq.tables.Category.CATEGORY
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Shelf.SHELF
import org.booklore.jooq.tables.UserBookFileProgress.USER_BOOK_FILE_PROGRESS
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class JooqAppBookDetailRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqAppBookDetailRepository
    @Autowired private lateinit var dsl: DSLContext

    private var user1 = 0L
    private var user2 = 0L
    private var lib1 = 0L
    private var book1 = 0L
    private var book2 = 0L
    private var audioBook = 0L
    private var deletedBook = 0L
    private var shelfA = 0L
    private var shelfB = 0L
    private var pdfFileId = 0L
    private var audioFileId = 0L

    private val lastRead = LocalDateTime.of(2026, 3, 3, 10, 0)
    private val koSync = LocalDateTime.of(2026, 3, 2, 9, 0)

    @BeforeEach
    fun setUp() {
        listOf(
            USER_BOOK_FILE_PROGRESS, USER_BOOK_PROGRESS,
            BOOK_SHELF_MAPPING, SHELF,
            BOOK_METADATA_CATEGORY_MAPPING, CATEGORY,
            BOOK_METADATA_AUTHOR_MAPPING, AUTHOR,
            BOOK_FILE, BOOK_METADATA, BOOK, LIBRARY, USERS
        ).forEach { dsl.deleteFrom(it).execute() }

        user1 = insertUser("alice")
        user2 = insertUser("bob")
        lib1 = insertLibrary("Main", """["PDF","EPUB"]""")

        book1 = insertBook(lib1, isDeleted = false, isPhysical = false)
        dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, book1)
            .set(BOOK_METADATA.TITLE, "The Hobbit")
            .set(BOOK_METADATA.SUBTITLE, "There and Back Again")
            .set(BOOK_METADATA.DESCRIPTION, "A hobbit's journey")
            .set(BOOK_METADATA.PUBLISHER, "Allen & Unwin")
            .set(BOOK_METADATA.PUBLISHED_DATE, LocalDate.of(1937, 9, 21))
            .set(BOOK_METADATA.PAGE_COUNT, 310)
            .set(BOOK_METADATA.ISBN_13, "9780261103344")
            .set(BOOK_METADATA.LANGUAGE, "en")
            .set(BOOK_METADATA.GOODREADS_RATING, 4.28)
            .set(BOOK_METADATA.GOODREADS_REVIEW_COUNT, 123)
            .set(BOOK_METADATA.SERIES_NAME, "LOTR")
            .set(BOOK_METADATA.SERIES_NUMBER, 0.5)
            .execute()
        linkAuthor(book1, insertAuthor("Tolkien"), 0)
        linkAuthor(book1, insertAuthor("Orwell"), 1)
        linkCategory(book1, insertCategory("Fantasy"))
        linkCategory(book1, insertCategory("Adventure"))
        insertFile(book1, "hobbit.epub", "EPUB", isBook = true)
        pdfFileId = insertFile(book1, "hobbit.pdf", "PDF", isBook = true)
        insertFile(book1, "cover.jpg", null, isBook = false)
        insertProgress(
            book1, user1, readStatus = "READ", rating = 4,
            epubCfi = "epubcfi(/6/4)", epubHref = "chap1", epubPercent = 50.0,
            koreaderPercent = 20.0, koreaderDevice = "Kindle", koreaderDeviceId = "kd-1", koSyncTime = koSync,
            lastReadTime = lastRead
        )

        book2 = insertBook(lib1, isDeleted = false, isPhysical = false)
        dsl.insertInto(BOOK_METADATA).set(BOOK_METADATA.BOOK_ID, book2).set(BOOK_METADATA.TITLE, "Extra").execute()

        // Shelf A holds book1 + book2 (bookCount 2); Shelf B holds book1 only (bookCount 1)
        shelfA = insertShelf(user1, "Favorites", "star", true)
        shelfB = insertShelf(user2, "Bob's", "heart", false)
        shelveBook(book1, shelfA); shelveBook(book2, shelfA); shelveBook(book1, shelfB)

        // Audiobook detail with file progress
        audioBook = insertBook(lib1, isDeleted = false, isPhysical = false)
        dsl.insertInto(BOOK_METADATA).set(BOOK_METADATA.BOOK_ID, audioBook).set(BOOK_METADATA.TITLE, "Hail Mary").execute()
        audioFileId = insertFile(audioBook, "phm.m4b", "AUDIOBOOK", isBook = true)
        dsl.insertInto(USER_BOOK_FILE_PROGRESS)
            .set(USER_BOOK_FILE_PROGRESS.USER_ID, user1)
            .set(USER_BOOK_FILE_PROGRESS.BOOK_FILE_ID, audioFileId)
            .set(USER_BOOK_FILE_PROGRESS.POSITION_DATA, "123456")
            .set(USER_BOOK_FILE_PROGRESS.POSITION_HREF, "3")
            .set(USER_BOOK_FILE_PROGRESS.PROGRESS_PERCENT, 45.0)
            .set(USER_BOOK_FILE_PROGRESS.LAST_READ_TIME, lastRead)
            .execute()

        deletedBook = insertBook(lib1, isDeleted = true, isPhysical = false)
        dsl.insertInto(BOOK_METADATA).set(BOOK_METADATA.BOOK_ID, deletedBook).set(BOOK_METADATA.TITLE, "Ghost").execute()
    }

    @Test
    fun `projects scalar metadata and library fields`() {
        val d = repository.findDetailById(book1, user1)!!
        assertThat(d.id).isEqualTo(book1)
        assertThat(d.title).isEqualTo("The Hobbit")
        assertThat(d.subtitle).isEqualTo("There and Back Again")
        assertThat(d.description).isEqualTo("A hobbit's journey")
        assertThat(d.publisher).isEqualTo("Allen & Unwin")
        assertThat(d.publishedDate).isEqualTo(LocalDate.of(1937, 9, 21))
        assertThat(d.pageCount).isEqualTo(310)
        assertThat(d.isbn13).isEqualTo("9780261103344")
        assertThat(d.language).isEqualTo("en")
        assertThat(d.goodreadsRating).isEqualTo(4.28)
        assertThat(d.goodreadsReviewCount).isEqualTo(123)
        assertThat(d.seriesName).isEqualTo("LOTR")
        assertThat(d.seriesNumber).isEqualTo(0.5f)
        assertThat(d.libraryId).isEqualTo(lib1)
        assertThat(d.libraryName).isEqualTo("Main")
        assertThat(d.thumbnailUrl).isEqualTo("/api/books/$book1/cover")
        assertThat(d.isPhysical).isFalse()
    }

    @Test
    fun `projects authors, categories and shelves with book counts`() {
        val d = repository.findDetailById(book1, user1)!!
        assertThat(d.authors).containsExactly("Tolkien", "Orwell")
        assertThat(d.categories).containsExactlyInAnyOrder("Fantasy", "Adventure")

        assertThat(d.shelves).hasSize(2)
        val byId = d.shelves.associateBy { it.id }
        assertThat(byId[shelfA]!!.name).isEqualTo("Favorites")
        assertThat(byId[shelfA]!!.bookCount).isEqualTo(2)
        assertThat(byId[shelfA]!!.isPublicShelf).isTrue()
        assertThat(byId[shelfB]!!.bookCount).isEqualTo(1)
        assertThat(byId[shelfB]!!.isPublicShelf).isFalse()
    }

    @Test
    fun `projects files, file types and primary selection`() {
        val d = repository.findDetailById(book1, user1)!!

        // only book-format files with a type; cover.jpg excluded
        assertThat(d.files).hasSize(2)
        assertThat(d.files).extracting<String> { it.bookType }.containsExactlyInAnyOrder("EPUB", "PDF")
        val pdf = d.files.first { it.bookType == "PDF" }
        assertThat(pdf.isPrimary).isTrue()        // library prefers PDF
        assertThat(pdf.extension).isEqualTo("pdf")
        assertThat(pdf.id).isEqualTo(pdfFileId)
        assertThat(d.files.first { it.bookType == "EPUB" }.isPrimary).isFalse()

        assertThat(d.fileTypes).containsExactlyInAnyOrder("EPUB", "PDF")
        assertThat(d.primaryFileType).isEqualTo("PDF")
    }

    @Test
    fun `projects per-format progress objects and read progress coalesce`() {
        val d = repository.findDetailById(book1, user1)!!
        assertThat(d.readStatus).isEqualTo("READ")
        assertThat(d.personalRating).isEqualTo(4)
        assertThat(d.readProgress).isEqualTo(20.0f)   // koreader wins the coalesce

        assertThat(d.epubProgress).isNotNull
        assertThat(d.epubProgress.cfi).isEqualTo("epubcfi(/6/4)")
        assertThat(d.epubProgress.href).isEqualTo("chap1")
        assertThat(d.epubProgress.percentage).isEqualTo(50.0f)
        assertThat(d.epubProgress.updatedAt).isEqualTo(lastRead.toInstant(ZoneOffset.UTC))

        assertThat(d.koreaderProgress).isNotNull
        assertThat(d.koreaderProgress.percentage).isEqualTo(20.0f)
        assertThat(d.koreaderProgress.device).isEqualTo("Kindle")
        assertThat(d.koreaderProgress.deviceId).isEqualTo("kd-1")
        assertThat(d.koreaderProgress.lastSyncTime).isEqualTo(koSync.toInstant(ZoneOffset.UTC))

        assertThat(d.pdfProgress).isNull()
        assertThat(d.cbxProgress).isNull()
        assertThat(d.audiobookProgress).isNull()
    }

    @Test
    fun `projects audiobook progress from the most recent file progress`() {
        val d = repository.findDetailById(audioBook, user1)!!
        assertThat(d.audiobookProgress).isNotNull
        assertThat(d.audiobookProgress.positionMs).isEqualTo(123456L)
        assertThat(d.audiobookProgress.trackIndex).isEqualTo(3)
        assertThat(d.audiobookProgress.percentage).isEqualTo(45.0f)
        assertThat(d.audiobookProgress.updatedAt).isEqualTo(lastRead.toInstant(ZoneOffset.UTC))
    }

    @Test
    fun `returns null for missing or deleted books`() {
        assertThat(repository.findDetailById(999_999L, user1)).isNull()
        assertThat(repository.findDetailById(deletedBook, user1)).isNull()
    }

    @Test
    fun `progress is scoped to the requested user but shelves are not`() {
        val d = repository.findDetailById(book1, user2)!!
        assertThat(d.readStatus).isNull()
        assertThat(d.personalRating as Int?).isNull()
        assertThat(d.readProgress as Float?).isNull()
        assertThat(d.epubProgress).isNull()
        assertThat(d.koreaderProgress).isNull()
        // scalar + shelf membership independent of user
        assertThat(d.title).isEqualTo("The Hobbit")
        assertThat(d.shelves).hasSize(2)
        assertThat(d.primaryFileType).isEqualTo("PDF")
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

    private fun insertCategory(name: String): Long =
        dsl.insertInto(CATEGORY).set(CATEGORY.NAME, name)
            .returningResult(CATEGORY.ID).fetchOne()!!.get(CATEGORY.ID)!!

    private fun insertShelf(userId: Long, name: String, icon: String, public: Boolean): Long =
        dsl.insertInto(SHELF)
            .set(SHELF.USER_ID, userId).set(SHELF.NAME, name).set(SHELF.ICON, icon)
            .set(SHELF.IS_PUBLIC, if (public) 1.toByte() else 0.toByte())
            .returningResult(SHELF.ID).fetchOne()!!.get(SHELF.ID)!!

    private fun insertBook(libraryId: Long, isDeleted: Boolean, isPhysical: Boolean): Long {
        val insert = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .set(BOOK.ADDED_ON, LocalDateTime.now())
            .set(BOOK.IS_PHYSICAL, if (isPhysical) 1.toByte() else 0.toByte())
        if (isDeleted) insert.set(BOOK.DELETED, 1.toByte())
        return insert.returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
    }

    private fun linkAuthor(bookId: Long, authorId: Long, sortOrder: Int) {
        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, authorId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.SORT_ORDER, sortOrder)
            .execute()
    }

    private fun linkCategory(bookId: Long, categoryId: Long) {
        dsl.insertInto(BOOK_METADATA_CATEGORY_MAPPING)
            .set(BOOK_METADATA_CATEGORY_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_CATEGORY_MAPPING.CATEGORY_ID, categoryId)
            .execute()
    }

    private fun shelveBook(bookId: Long, shelfId: Long) {
        dsl.insertInto(BOOK_SHELF_MAPPING)
            .set(BOOK_SHELF_MAPPING.BOOK_ID, bookId)
            .set(BOOK_SHELF_MAPPING.SHELF_ID, shelfId)
            .execute()
    }

    private fun insertFile(bookId: Long, fileName: String, bookType: String?, isBook: Boolean): Long {
        val insert = dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, fileName)
            .set(BOOK_FILE.FILE_SUB_PATH, "/files")
            .set(BOOK_FILE.IS_BOOK, if (isBook) 1.toByte() else 0.toByte())
        if (bookType != null) insert.set(BOOK_FILE.BOOK_TYPE, bookType)
        return insert.returningResult(BOOK_FILE.ID).fetchOne()!!.get(BOOK_FILE.ID)!!
    }

    private fun insertProgress(
        bookId: Long, userId: Long, readStatus: String?, rating: Int?,
        epubCfi: String? = null, epubHref: String? = null, epubPercent: Double? = null,
        koreaderPercent: Double? = null, koreaderDevice: String? = null, koreaderDeviceId: String? = null,
        koSyncTime: LocalDateTime? = null, lastReadTime: LocalDateTime? = null
    ) {
        val insert = dsl.insertInto(USER_BOOK_PROGRESS)
            .set(USER_BOOK_PROGRESS.USER_ID, userId)
            .set(USER_BOOK_PROGRESS.BOOK_ID, bookId)
        if (readStatus != null) insert.set(USER_BOOK_PROGRESS.READ_STATUS, readStatus)
        if (rating != null) insert.set(USER_BOOK_PROGRESS.PERSONAL_RATING, rating.toByte())
        if (epubCfi != null) insert.set(USER_BOOK_PROGRESS.EPUB_PROGRESS, epubCfi)
        if (epubHref != null) insert.set(USER_BOOK_PROGRESS.EPUB_PROGRESS_HREF, epubHref)
        if (epubPercent != null) insert.set(USER_BOOK_PROGRESS.EPUB_PROGRESS_PERCENT, epubPercent)
        if (koreaderPercent != null) insert.set(USER_BOOK_PROGRESS.KOREADER_PROGRESS_PERCENT, koreaderPercent)
        if (koreaderDevice != null) insert.set(USER_BOOK_PROGRESS.KOREADER_DEVICE, koreaderDevice)
        if (koreaderDeviceId != null) insert.set(USER_BOOK_PROGRESS.KOREADER_DEVICE_ID, koreaderDeviceId)
        if (koSyncTime != null) insert.set(USER_BOOK_PROGRESS.KOREADER_LAST_SYNC_TIME, koSyncTime)
        if (lastReadTime != null) insert.set(USER_BOOK_PROGRESS.LAST_READ_TIME, lastReadTime)
        insert.execute()
    }
}
