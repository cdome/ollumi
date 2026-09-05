package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.LibraryPath.LIBRARY_PATH
import org.booklore.model.enums.BookFileType
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class JooqBookRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repository: JooqBookRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private var lib1Id = 0L
    private var lib2Id = 0L
    private var path1Id = 0L   // lib1
    private var path2Id = 0L   // lib2
    private var book1Id = 0L   // lib1/path1, "The Hobbit", series LOTR, EPUB, cover updated
    private var book2Id = 0L   // lib1/path1, "The Two Towers", series LOTR, EPUB + AUDIOBOOK
    private var book3Id = 0L   // lib2/path2, "1984", no series, AUDIOBOOK
    private var book4Id = 0L   // lib1/path1, metadata without title/series, non-book EPUB attachment + PDF book file
    private var book5Id = 0L   // lib2/path2, "Dune", series Dune, EPUB
    private var deletedBookId = 0L  // lib1/path1, deleted, series GhostSeries, EPUB

    private val coverUpdatedOn: LocalDateTime = LocalDateTime.of(2026, 1, 15, 10, 30)

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(BOOK_FILE).execute()
        dsl.deleteFrom(BOOK_METADATA).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY_PATH).execute()
        dsl.deleteFrom(LIBRARY).execute()

        lib1Id = insertLibrary("Fantasy Library")
        lib2Id = insertLibrary("SciFi Library")
        path1Id = insertLibraryPath(lib1Id, "/books/fantasy")
        path2Id = insertLibraryPath(lib2Id, "/books/scifi")

        book1Id = insertBook(lib1Id, path1Id)
        book2Id = insertBook(lib1Id, path1Id)
        book3Id = insertBook(lib2Id, path2Id)
        book4Id = insertBook(lib1Id, path1Id)
        book5Id = insertBook(lib2Id, path2Id)
        deletedBookId = insertBook(lib1Id, path1Id, deleted = true)

        // Series numbers deliberately inverted vs insertion order to exercise ordering
        insertMetadata(book1Id, title = "The Hobbit", seriesName = "LOTR", seriesNumber = 2.0, coverUpdatedOn = coverUpdatedOn)
        insertMetadata(book2Id, title = "The Two Towers", seriesName = "LOTR", seriesNumber = 1.0)
        insertMetadata(book3Id, title = "1984")
        insertMetadata(book4Id, title = null)
        insertMetadata(book5Id, title = "Dune", seriesName = "Dune")
        insertMetadata(deletedBookId, title = "Ghost", seriesName = "GhostSeries")

        insertBookFile(book1Id, "hobbit.epub", "EPUB", isBook = true)
        insertBookFile(book2Id, "towers.epub", "EPUB", isBook = true)
        insertBookFile(book2Id, "towers.m4b", "AUDIOBOOK", isBook = true)
        insertBookFile(book3Id, "1984.m4b", "AUDIOBOOK", isBook = true)
        // Non-book attachment inserted first (lower ID) — must be ignored by book-format filters
        insertBookFile(book4Id, "cover-art.jpg", "EPUB", isBook = false)
        insertBookFile(book4Id, "orphan-book.pdf", "PDF", isBook = true)
        insertBookFile(book5Id, "dune.epub", "EPUB", isBook = true)
        insertBookFile(deletedBookId, "ghost.epub", "EPUB", isBook = true)
    }

    // =============================================================
    // findBookIdsByLibraryId
    // =============================================================

    @Test
    fun `findBookIdsByLibraryId returns non-deleted books of the library`() {
        val ids = repository.findBookIdsByLibraryId(lib1Id)

        assertThat(ids).containsExactlyInAnyOrder(book1Id, book2Id, book4Id)
    }

    @Test
    fun `findBookIdsByLibraryId excludes other libraries`() {
        val ids = repository.findBookIdsByLibraryId(lib2Id)

        assertThat(ids).containsExactlyInAnyOrder(book3Id, book5Id)
    }

    // =============================================================
    // findBookIdsByLibraryPathIds
    // =============================================================

    @Test
    fun `findBookIdsByLibraryPathIds returns books under the given paths`() {
        val ids = repository.findBookIdsByLibraryPathIds(listOf(path1Id))

        assertThat(ids).containsExactlyInAnyOrder(book1Id, book2Id, book4Id)
    }

    @Test
    fun `findBookIdsByLibraryPathIds combines multiple paths`() {
        val ids = repository.findBookIdsByLibraryPathIds(listOf(path1Id, path2Id))

        assertThat(ids).containsExactlyInAnyOrder(book1Id, book2Id, book3Id, book4Id, book5Id)
    }

    @Test
    fun `findBookIdsByLibraryPathIds returns empty for empty input`() {
        val ids = repository.findBookIdsByLibraryPathIds(emptyList())

        assertThat(ids).isEmpty()
    }

    // =============================================================
    // Counts
    // =============================================================

    @Test
    fun `countByIds counts only existing non-deleted books`() {
        val count = repository.countByIds(listOf(book1Id, book2Id, deletedBookId, 999_999L))

        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `countByLibraryId counts non-deleted books per library`() {
        assertThat(repository.countByLibraryId(lib1Id)).isEqualTo(3)
        assertThat(repository.countByLibraryId(lib2Id)).isEqualTo(2)
    }

    @Test
    fun `countByBookType counts distinct books with book-format files of the type`() {
        // book4's EPUB attachment is not a book format, deleted book's EPUB is excluded
        assertThat(repository.countByBookType(BookFileType.EPUB)).isEqualTo(3)
        assertThat(repository.countByBookType(BookFileType.AUDIOBOOK)).isEqualTo(2)
        assertThat(repository.countByBookType(BookFileType.PDF)).isEqualTo(1)
    }

    @Test
    fun `countByLibraryIdAndBookType scopes the count to a library`() {
        assertThat(repository.countByLibraryIdAndBookType(lib1Id, BookFileType.EPUB)).isEqualTo(2)
        assertThat(repository.countByLibraryIdAndBookType(lib2Id, BookFileType.EPUB)).isEqualTo(1)
        assertThat(repository.countByLibraryIdAndBookType(lib1Id, BookFileType.AUDIOBOOK)).isEqualTo(1)
        assertThat(repository.countByLibraryIdAndBookType(lib2Id, BookFileType.PDF)).isEqualTo(0)
    }

    @Test
    fun `countSoftDeleted counts only soft-deleted books`() {
        assertThat(repository.countSoftDeleted()).isEqualTo(1)
    }

    // =============================================================
    // findCoverUpdateInfoByIds
    // =============================================================

    @Test
    fun `findCoverUpdateInfoByIds returns cover timestamps as UTC instants`() {
        val updates = repository.findCoverUpdateInfoByIds(listOf(book1Id, book4Id))

        assertThat(updates).hasSize(2)
        val byId = updates.associateBy { it.id }
        assertThat(byId[book1Id]!!.coverUpdatedOn)
            .isEqualTo(coverUpdatedOn.toInstant(ZoneOffset.UTC))
        assertThat(byId[book4Id]!!.coverUpdatedOn).isNull()
    }

    @Test
    fun `findCoverUpdateInfoByIds includes deleted books like the JPA query did`() {
        val updates = repository.findCoverUpdateInfoByIds(listOf(deletedBookId))

        assertThat(updates.map { it.id }).containsExactly(deletedBookId)
    }

    // =============================================================
    // findDistinctSeriesNamesGrouped
    // =============================================================

    @Test
    fun `grouped series names fall back to the unknown label per library`() {
        val names = repository.findDistinctSeriesNamesGrouped("Unknown Series", lib1Id)

        assertThat(names).containsExactly("LOTR", "Unknown Series")
    }

    @Test
    fun `grouped series names across all libraries are distinct and sorted`() {
        val names = repository.findDistinctSeriesNamesGrouped("Unknown Series", null)

        assertThat(names).containsExactly("Dune", "LOTR", "Unknown Series")
    }

    @Test
    fun `grouped series names exclude deleted books`() {
        val names = repository.findDistinctSeriesNamesGrouped("Unknown Series", null)

        assertThat(names).doesNotContain("GhostSeries")
    }

    // =============================================================
    // findDistinctSeriesNamesUngrouped
    // =============================================================

    @Test
    fun `ungrouped series names fall back to title then first book file name`() {
        val names = repository.findDistinctSeriesNamesUngrouped(lib1Id)

        // book1/book2 -> LOTR, book4 -> no title, first book-format file name
        assertThat(names).containsExactly("LOTR", "orphan-book.pdf")
    }

    @Test
    fun `ungrouped series names across all libraries include title fallbacks`() {
        val names = repository.findDistinctSeriesNamesUngrouped(null)

        assertThat(names).containsExactly("1984", "Dune", "LOTR", "orphan-book.pdf")
    }

    // =============================================================
    // findBookIdsBySeriesNameGrouped / Ungrouped
    // =============================================================

    @Test
    fun `grouped series book ids are ordered by series number`() {
        val ids = repository.findBookIdsBySeriesNameGrouped("LOTR", lib1Id)

        // book2 is series #1, book1 is series #2
        assertThat(ids).containsExactly(book2Id, book1Id)
    }

    @Test
    fun `grouped series book ids fall back to first book file name only`() {
        // book4 has no series name -> matched by its first book-format file name
        assertThat(repository.findBookIdsBySeriesNameGrouped("orphan-book.pdf", lib1Id))
            .containsExactly(book4Id)
        // grouped mode has no title fallback (JPA parity): book3's title does not match
        assertThat(repository.findBookIdsBySeriesNameGrouped("1984", lib2Id)).isEmpty()
    }

    @Test
    fun `grouped series book ids exclude deleted books and other libraries`() {
        assertThat(repository.findBookIdsBySeriesNameGrouped("GhostSeries", lib1Id)).isEmpty()
        assertThat(repository.findBookIdsBySeriesNameGrouped("LOTR", lib2Id)).isEmpty()
    }

    @Test
    fun `ungrouped series book ids match series then title then file name`() {
        assertThat(repository.findBookIdsBySeriesNameUngrouped("LOTR", lib1Id))
            .containsExactly(book2Id, book1Id)
        assertThat(repository.findBookIdsBySeriesNameUngrouped("1984", lib2Id))
            .containsExactly(book3Id)
        assertThat(repository.findBookIdsBySeriesNameUngrouped("orphan-book.pdf", lib1Id))
            .containsExactly(book4Id)
    }

    // =============================================================
    // Writes: soft-delete cleanup and library moves
    // =============================================================

    @Test
    fun `deleteAllSoftDeleted removes soft-deleted books and cascades to children`() {
        val removed = repository.deleteAllSoftDeleted()

        assertThat(removed).isEqualTo(1)
        assertThat(repository.countSoftDeleted()).isZero()
        // non-deleted books untouched
        assertThat(dsl.fetchCount(BOOK)).isEqualTo(5)
        // children of the deleted book are gone via FK cascade
        assertThat(dsl.fetchCount(BOOK_FILE, BOOK_FILE.BOOK_ID.eq(deletedBookId))).isZero()
    }

    @Test
    fun `deleteSoftDeletedBefore removes only books deleted before the cutoff`() {
        val oldDeletedId = insertBook(lib1Id, path1Id, deleted = true)
        dsl.update(BOOK).set(BOOK.DELETED_AT, LocalDateTime.now().minusDays(30))
            .where(BOOK.ID.eq(oldDeletedId)).execute()
        val recentDeletedId = insertBook(lib1Id, path1Id, deleted = true)
        dsl.update(BOOK).set(BOOK.DELETED_AT, LocalDateTime.now().minusDays(1))
            .where(BOOK.ID.eq(recentDeletedId)).execute()

        val removed = repository.deleteSoftDeletedBefore(Instant.now().minus(7, ChronoUnit.DAYS))

        assertThat(removed).isEqualTo(1)
        val remainingIds = dsl.select(BOOK.ID).from(BOOK).fetch(BOOK.ID)
        assertThat(remainingIds).doesNotContain(oldDeletedId)
        // recent deletion and the fixture's deleted book (deleted_at is null) survive
        assertThat(remainingIds).contains(recentDeletedId, deletedBookId)
    }

    @Test
    fun `updateLibrary moves a book to another library and path`() {
        repository.updateLibrary(book1Id, lib2Id, path2Id)

        val row = dsl.select(BOOK.LIBRARY_ID, BOOK.LIBRARY_PATH_ID)
            .from(BOOK).where(BOOK.ID.eq(book1Id)).fetchOne()!!
        assertThat(row[BOOK.LIBRARY_ID]).isEqualTo(lib2Id)
        assertThat(row[BOOK.LIBRARY_PATH_ID]).isEqualTo(path2Id)
    }

    // =============================================================
    // Helpers
    // =============================================================

    private fun insertLibrary(name: String): Long =
        dsl.insertInto(LIBRARY)
            .set(LIBRARY.NAME, name)
            .returningResult(LIBRARY.ID)
            .fetchOne()!!.get(LIBRARY.ID)!!

    private fun insertLibraryPath(libraryId: Long, path: String): Long =
        dsl.insertInto(LIBRARY_PATH)
            .set(LIBRARY_PATH.LIBRARY_ID, libraryId)
            .set(LIBRARY_PATH.PATH, path)
            .returningResult(LIBRARY_PATH.ID)
            .fetchOne()!!.get(LIBRARY_PATH.ID)!!

    private fun insertBook(libraryId: Long, libraryPathId: Long, deleted: Boolean = false): Long {
        val insert = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .set(BOOK.LIBRARY_PATH_ID, libraryPathId)
            .set(BOOK.ADDED_ON, LocalDateTime.now())
        if (deleted) insert.set(BOOK.DELETED, 1.toByte())
        return insert.returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
    }

    private fun insertMetadata(
        bookId: Long,
        title: String?,
        seriesName: String? = null,
        seriesNumber: Double? = null,
        coverUpdatedOn: LocalDateTime? = null
    ) {
        val insert = dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, bookId)
        if (title != null) insert.set(BOOK_METADATA.TITLE, title)
        if (seriesName != null) insert.set(BOOK_METADATA.SERIES_NAME, seriesName)
        if (seriesNumber != null) insert.set(BOOK_METADATA.SERIES_NUMBER, seriesNumber)
        if (coverUpdatedOn != null) insert.set(BOOK_METADATA.COVER_UPDATED_ON, coverUpdatedOn)
        insert.execute()
    }

    private fun insertBookFile(bookId: Long, fileName: String, bookType: String, isBook: Boolean) {
        dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, fileName)
            .set(BOOK_FILE.FILE_SUB_PATH, "/files")
            .set(BOOK_FILE.BOOK_TYPE, bookType)
            .set(BOOK_FILE.IS_BOOK, if (isBook) 1.toByte() else 0.toByte())
            .execute()
    }
}
