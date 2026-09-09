package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.ComicMetadata.COMIC_METADATA
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.LibraryPath.LIBRARY_PATH
import org.booklore.model.enums.BookFileType
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Verifies JooqBookEntityRepository reconstructs the SAME BookEntity object graph shape the
 * replaced BookRepository @EntityGraph/native-query finders produced - not just "some data comes
 * back", but that downstream consumer patterns which broke during manual review
 * (getPrimaryBookFile()/getFullFilePath() needing library.formatPriority + libraryPath, comic
 * metadata scalar round-trip, chapters JSON round-trip) actually work end to end.
 */
class JooqBookEntityRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repository: JooqBookEntityRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private var libraryId = 0L
    private var libraryPathId = 0L
    private var fullBookId = 0L      // EPUB + AUDIOBOOK(with chapters) + non-book attachment, full metadata + comic
    private var filelessBookId = 0L  // metadata only, no files
    private var deletedBookId = 0L

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(BOOK_FILE).execute()
        dsl.deleteFrom(COMIC_METADATA).execute()
        dsl.deleteFrom(BOOK_METADATA).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY_PATH).execute()
        dsl.deleteFrom(LIBRARY).execute()

        libraryId = dsl.insertInto(LIBRARY)
            .set(LIBRARY.NAME, "Fantasy Library")
            .set(LIBRARY.SORT, "title,ASCENDING")
            .set(LIBRARY.WATCH, 1.toByte())
            .set(LIBRARY.ICON, "book")
            .set(LIBRARY.ICON_TYPE, "PRIME_NG")
            .set(LIBRARY.FORMAT_PRIORITY, """["EPUB","PDF"]""")
            .set(LIBRARY.ALLOWED_FORMATS, """["EPUB","PDF","AUDIOBOOK"]""")
            .set(LIBRARY.ORGANIZATION_MODE, "BOOK_PER_FOLDER")
            .set(LIBRARY.METADATA_SOURCE, "EMBEDDED")
            .returningResult(LIBRARY.ID)
            .fetchOne()!!.get(LIBRARY.ID)!!

        libraryPathId = dsl.insertInto(LIBRARY_PATH)
            .set(LIBRARY_PATH.LIBRARY_ID, libraryId)
            .set(LIBRARY_PATH.PATH, "/books/fantasy")
            .returningResult(LIBRARY_PATH.ID)
            .fetchOne()!!.get(LIBRARY_PATH.ID)!!

        fullBookId = insertBook(deleted = false, coverHash = "coverhash1", audiobookCoverHash = "audhash1", matchScore = 87.5)
        filelessBookId = insertBook(deleted = false)
        deletedBookId = insertBook(deleted = true)

        insertFullMetadata(fullBookId)
        insertComicMetadata(fullBookId)
        dsl.insertInto(BOOK_METADATA).set(BOOK_METADATA.BOOK_ID, filelessBookId).set(BOOK_METADATA.TITLE, "Fileless").execute()
        dsl.insertInto(BOOK_METADATA).set(BOOK_METADATA.BOOK_ID, deletedBookId).set(BOOK_METADATA.TITLE, "Deleted Book").execute()

        // Non-book attachment inserted first (lower id) to verify book-format filtering in getPrimaryBookFile()
        insertBookFile(fullBookId, "cover-scan.jpg", null, isBook = false)
        insertBookFile(fullBookId, "book.epub", "EPUB", isBook = true)
        insertAudiobookFile(fullBookId, "book.m4b")
        insertBookFile(deletedBookId, "ghost.epub", "EPUB", isBook = true)
    }

    // =============================================================
    // findByIdFull
    // =============================================================

    @Test
    fun `findByIdFull returns full graph including comic metadata and bookFiles`() {
        val book = repository.findByIdFull(fullBookId)!!

        assertThat(book.metadata.title).isEqualTo("Full Book")
        assertThat(book.metadata.comicMetadata).isNotNull
        assertThat(book.metadata.comicMetadata.issueNumber).isEqualTo("12")
        assertThat(book.bookFiles).hasSize(3)
    }

    @Test
    fun `findByIdFull also populates library and libraryPath so BookMapper-style consumers work`() {
        // Regression: BookMetadataService.processSingleBookUpdate maps the result through BookMapper,
        // which resolves the primary file's full path - needs library.formatPriority AND libraryPath.
        val book = repository.findByIdFull(fullBookId)!!

        assertThat(book.primaryBookFile.fileName).isEqualTo("book.epub")
        assertThat(book.fullFilePath.toString()).isEqualTo("/books/fantasy/book.epub")
    }

    @Test
    fun `findByIdFull returns null for missing or deleted book`() {
        assertThat(repository.findByIdFull(999_999L)).isNull()
        assertThat(repository.findByIdFull(deletedBookId)).isNull()
    }

    // =============================================================
    // findByCurrentHash
    // =============================================================

    @Test
    fun `findByCurrentHash resolves the book owning a book-format file with that hash`() {
        val book = repository.findByCurrentHash("epubhash")!!

        assertThat(book.id).isEqualTo(fullBookId)
        assertThat(book.library.id).isEqualTo(libraryId)
        assertThat(book.libraryPath.path).isEqualTo("/books/fantasy")
    }

    @Test
    fun `findByCurrentHash ignores non-book-format attachments`() {
        assertThat(repository.findByCurrentHash("coverscanhash")).isNull()
    }

    // =============================================================
    // findDeletedByLibraryId
    // =============================================================

    @Test
    fun `findDeletedByLibraryId returns only soft-deleted books with full graph`() {
        val books = repository.findDeletedByLibraryId(libraryId)

        assertThat(books).hasSize(1)
        val book = books.first()
        assertThat(book.id).isEqualTo(deletedBookId)
        assertThat(book.metadata.title).isEqualTo("Deleted Book")
        assertThat(book.getFullFilePath().toString()).isEqualTo("/books/fantasy/ghost.epub")
    }

    // =============================================================
    // findAllWithMetadata
    // =============================================================

    @Test
    fun `findAllWithMetadata returns every non-deleted book with metadata attached`() {
        val books = repository.findAllWithMetadata()

        assertThat(books.map { it.id }).containsExactlyInAnyOrder(fullBookId, filelessBookId)
        assertThat(books.first { it.id == fullBookId }.metadata.title).isEqualTo("Full Book")
    }

    // =============================================================
    // findAllByLibraryIdWithFiles
    // =============================================================

    @Test
    fun `findAllByLibraryIdWithFiles scopes to the library and includes bookFiles`() {
        val books = repository.findAllByLibraryIdWithFiles(libraryId)

        val full = books.first { it.id == fullBookId }
        assertThat(full.bookFiles).hasSize(3)
        assertThat(full.metadata).isNotNull
    }

    // =============================================================
    // findAllForDuplicateDetection
    // =============================================================

    @Test
    fun `findAllForDuplicateDetection includes metadata, bookFiles and libraryPath`() {
        val books = repository.findAllForDuplicateDetection(libraryId)

        val full = books.first { it.id == fullBookId }
        assertThat(full.metadata.title).isEqualTo("Full Book")
        assertThat(full.libraryPath.path).isEqualTo("/books/fantasy")
        assertThat(full.bookFiles).hasSize(3)
    }

    // =============================================================
    // findAllWithMetadataByFileSizeKbIsNull
    // =============================================================

    @Test
    fun `findAllWithMetadataByFileSizeKbIsNull finds book-format files missing a size`() {
        val books = repository.findAllWithMetadataByFileSizeKbIsNull()

        assertThat(books.map { it.id }).contains(fullBookId)
        // Full path resolution must work (library.formatPriority + libraryPath wired correctly)
        val full = books.first { it.id == fullBookId }
        assertThat(full.fullFilePath).isNotNull
    }

    // =============================================================
    // findAllFullBooks
    // =============================================================

    @Test
    fun `findAllFullBooks returns metadata and comicMetadata without touching bookFiles`() {
        val books = repository.findAllFullBooks()

        assertThat(books.map { it.id }).containsExactlyInAnyOrder(fullBookId, filelessBookId)
        val full = books.first { it.id == fullBookId }
        assertThat(full.metadata.comicMetadata.volumeName).isEqualTo("Volume One")
        // bookFiles deliberately not populated for this method - matches the JPA EntityGraph it replaces
        assertThat(full.bookFiles).isEmpty()
    }

    // =============================================================
    // findBooksWithFilesUnderPath
    // =============================================================

    @Test
    fun `findBooksWithFilesUnderPath matches files under the given folder prefix`() {
        val books = repository.findBooksWithFilesUnderPath(libraryPathId, "")

        assertThat(books.map { it.id }).contains(fullBookId)
        val full = books.first { it.id == fullBookId }
        assertThat(full.metadata).isNotNull
        assertThat(full.library.id).isEqualTo(libraryId)
    }

    // =============================================================
    // findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName
    // =============================================================

    @Test
    fun `findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName returns full graph for the duplicate-file-rediscovery path`() {
        // Regression: BookCreatorService.createShellBook's "book already exists" branch feeds this
        // result into AbstractFileProcessor.createAndMapBook, which unconditionally calls
        // metadataMatchService.calculateMatchScore (needs metadata), saveConnections (NPEs on null
        // metadata), and bookMapper.toBook (needs the full graph).
        val book = repository.findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
            libraryId, libraryPathId, "", "book.epub"
        )!!

        assertThat(book.id).isEqualTo(fullBookId)
        assertThat(book.metadata).isNotNull
        assertThat(book.metadata.title).isEqualTo("Full Book")
        assertThat(book.metadata.comicMetadata).isNotNull
        assertThat(book.library.formatPriority).containsExactly(BookFileType.EPUB, BookFileType.PDF)
        assertThat(book.libraryPath.path).isEqualTo("/books/fantasy")
        assertThat(book.primaryBookFile.fileName).isEqualTo("book.epub")
    }

    @Test
    fun `findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName returns null when nothing matches`() {
        assertThat(
            repository.findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(libraryId, libraryPathId, "", "missing.epub")
        ).isNull()
    }

    // =============================================================
    // Chapters / audio technical fields round-trip
    // =============================================================

    @Test
    fun `audiobook file's chapters and technical fields round-trip through JSON`() {
        val book = repository.findByIdFull(fullBookId)!!

        val audioFile = book.bookFiles.first { it.bookType == BookFileType.AUDIOBOOK }
        assertThat(audioFile.durationSeconds).isEqualTo(3600L)
        assertThat(audioFile.bitrate).isEqualTo(128)
        assertThat(audioFile.chapterCount).isEqualTo(2)
        assertThat(audioFile.chapters).hasSize(2)
        assertThat(audioFile.chapters[0].title).isEqualTo("Chapter 1")
        assertThat(audioFile.chapters[1].startTimeMs).isEqualTo(1800000L)
    }

    // =============================================================
    // Helpers
    // =============================================================

    private fun insertBook(deleted: Boolean, coverHash: String? = null, audiobookCoverHash: String? = null, matchScore: Double? = null): Long {
        val insert = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .set(BOOK.LIBRARY_PATH_ID, libraryPathId)
            .set(BOOK.ADDED_ON, LocalDateTime.now())
            .set(BOOK.BOOK_COVER_HASH, coverHash)
            .set(BOOK.AUDIOBOOK_COVER_HASH, audiobookCoverHash)
            .set(BOOK.METADATA_MATCH_SCORE, matchScore)
        if (deleted) insert.set(BOOK.DELETED, 1.toByte())
        return insert.returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
    }

    private fun insertFullMetadata(bookId: Long) {
        dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, bookId)
            .set(BOOK_METADATA.TITLE, "Full Book")
            .set(BOOK_METADATA.SUBTITLE, "A Subtitle")
            .set(BOOK_METADATA.PUBLISHER, "Acme Press")
            .set(BOOK_METADATA.PUBLISHED_DATE, LocalDate.of(2020, 1, 1))
            .set(BOOK_METADATA.DESCRIPTION, "A description")
            .set(BOOK_METADATA.SERIES_NAME, "The Series")
            .set(BOOK_METADATA.SERIES_NUMBER, 1.0)
            .set(BOOK_METADATA.ISBN_13, "9781234567890")
            .set(BOOK_METADATA.LANGUAGE, "en")
            .set(BOOK_METADATA.RATING, 4.5)
            .set(BOOK_METADATA.REVIEW_COUNT, 10)
            .set(BOOK_METADATA.NARRATOR, "Jane Narrator")
            .set(BOOK_METADATA.ABRIDGED, 0.toByte())
            .set(BOOK_METADATA.EMBEDDING_VECTOR, "[0.1, 0.2, 0.3]")
            .set(BOOK_METADATA.SEARCH_TEXT, "full book the series jane narrator")
            .set(BOOK_METADATA.TITLE_LOCKED, 1.toByte())
            .set(BOOK_METADATA.AUTHORS_LOCKED, 1.toByte())
            .execute()
    }

    private fun insertComicMetadata(bookId: Long) {
        dsl.insertInto(COMIC_METADATA)
            .set(COMIC_METADATA.BOOK_ID, bookId)
            .set(COMIC_METADATA.ISSUE_NUMBER, "12")
            .set(COMIC_METADATA.VOLUME_NAME, "Volume One")
            .set(COMIC_METADATA.VOLUME_NUMBER, 1)
            .set(COMIC_METADATA.MANGA, 1.toByte())
            .set(COMIC_METADATA.READING_DIRECTION, "rtl")
            .set(COMIC_METADATA.ISSUE_NUMBER_LOCKED, 1.toByte())
            .execute()
    }

    private fun insertBookFile(bookId: Long, fileName: String, bookType: String?, isBook: Boolean) {
        dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, fileName)
            .set(BOOK_FILE.FILE_SUB_PATH, "")
            .set(BOOK_FILE.BOOK_TYPE, bookType)
            .set(BOOK_FILE.IS_BOOK, if (isBook) 1.toByte() else 0.toByte())
            .set(BOOK_FILE.CURRENT_HASH, if (fileName == "book.epub") "epubhash" else if (fileName == "cover-scan.jpg") "coverscanhash" else null)
            .execute()
    }

    private fun insertAudiobookFile(bookId: Long, fileName: String) {
        dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, fileName)
            .set(BOOK_FILE.FILE_SUB_PATH, "")
            .set(BOOK_FILE.BOOK_TYPE, "AUDIOBOOK")
            .set(BOOK_FILE.IS_BOOK, 1.toByte())
            .set(BOOK_FILE.DURATION_SECONDS, 3600L)
            .set(BOOK_FILE.BITRATE, 128)
            .set(BOOK_FILE.SAMPLE_RATE, 44100)
            .set(BOOK_FILE.CHANNELS, 2)
            .set(BOOK_FILE.CODEC, "AAC")
            .set(BOOK_FILE.CHAPTER_COUNT, 2)
            .set(
                BOOK_FILE.CHAPTERS_JSON,
                """[{"index":0,"title":"Chapter 1","startTimeMs":0,"endTimeMs":1800000,"durationMs":1800000},""" +
                    """{"index":1,"title":"Chapter 2","startTimeMs":1800000,"endTimeMs":3600000,"durationMs":1800000}]"""
            )
            .execute()
    }
}
