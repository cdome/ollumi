package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.enums.ReadStatus
import org.booklore.repository.jooq.dto.CompletionTimelineEntry
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDateTime

class JooqUserBookProgressRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repository: JooqUserBookProgressRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private val ubp = USER_BOOK_PROGRESS

    private var user1Id = 0L
    private var user2Id = 0L
    private var libId = 0L
    private var book1Id = 0L
    private var book2Id = 0L
    private var book3Id = 0L
    private var book4Id = 0L

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(USER_BOOK_PROGRESS).execute()
        dsl.deleteFrom(BOOK_FILE).execute()
        dsl.deleteFrom(BOOK_METADATA).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY).execute()
        dsl.deleteFrom(USERS).execute()

        user1Id = insertUser("reader-one")
        user2Id = insertUser("reader-two")

        libId = dsl.insertInto(LIBRARY)
            .set(LIBRARY.NAME, "Library")
            .returningResult(LIBRARY.ID)
            .fetchOne()!!.get(LIBRARY.ID)!!

        book1Id = insertBook()
        book2Id = insertBook()
        book3Id = insertBook()
        book4Id = insertBook()

        // user1/book1: finished in March 2026, fully read EPUB, has KOReader + Kobo state
        insertProgress(
            user1Id, book1Id,
            readStatus = "READ", rating = 5,
            dateFinished = LocalDateTime.of(2026, 3, 15, 12, 0),
            lastReadTime = LocalDateTime.of(2026, 3, 15, 11, 0),
            epubProgressPercent = 100.0,
            koreaderProgressPercent = 55.0,
            koboProgressPercent = 10.0
        )
        // user1/book2: in progress, timeline falls back to readStatusModifiedTime (May 2026)
        insertProgress(
            user1Id, book2Id,
            readStatus = "READING", rating = 3,
            readStatusModifiedTime = LocalDateTime.of(2026, 5, 10, 9, 0),
            pdfProgressPercent = 40.0
        )
        // user1/book3: UNSET status — excluded from timeline and status distribution
        insertProgress(
            user1Id, book3Id,
            readStatus = "UNSET",
            lastReadTime = LocalDateTime.of(2025, 8, 1, 20, 0),
            cbxProgressPercent = 75.0
        )
        // user1/book4: READ, timeline falls back to lastReadTime (Aug 2025), no rating
        insertProgress(
            user1Id, book4Id,
            readStatus = "READ",
            lastReadTime = LocalDateTime.of(2025, 8, 1, 20, 0)
        )
        // user2/book1: other user's data — must never leak into user1 results
        insertProgress(
            user2Id, book1Id,
            readStatus = "READ", rating = 4,
            dateFinished = LocalDateTime.of(2026, 3, 20, 10, 0)
        )
    }

    // =============================================================
    // Statistics
    // =============================================================

    @Test
    fun `completion timeline groups by month and status with coalesce fallbacks, newest first`() {
        val entries = repository.findCompletionTimelineByUser(user1Id, 2026)

        assertThat(entries).containsExactly(
            CompletionTimelineEntry(2026, 5, ReadStatus.READING, 1),
            CompletionTimelineEntry(2026, 3, ReadStatus.READ, 1)
        )
    }

    @Test
    fun `completion timeline falls back to lastReadTime and filters by year`() {
        val entries = repository.findCompletionTimelineByUser(user1Id, 2025)

        // book4 (READ, lastReadTime Aug 2025); book3 is UNSET and excluded
        assertThat(entries).containsExactly(
            CompletionTimelineEntry(2025, 8, ReadStatus.READ, 1)
        )
    }

    @Test
    fun `heatmap counts only rows with dateFinished for the user`() {
        val entries = repository.findBookCompletionHeatmap(user1Id, 2017, 2026)

        assertThat(entries).hasSize(1)
        assertThat(entries[0].year).isEqualTo(2026)
        assertThat(entries[0].month).isEqualTo(3)
        assertThat(entries[0].count).isEqualTo(1)
    }

    @Test
    fun `rating distribution is ordered by rating and skips unrated rows`() {
        val entries = repository.findRatingDistributionByUser(user1Id)

        assertThat(entries.map { it.rating to it.count }).containsExactly(3 to 1L, 5 to 1L)
    }

    @Test
    fun `status distribution excludes UNSET`() {
        val entries = repository.findStatusDistributionByUser(user1Id)

        assertThat(entries.associate { it.status to it.count })
            .isEqualTo(mapOf(ReadStatus.READ to 2L, ReadStatus.READING to 1L))
    }

    @Test
    fun `progress percents returns one row per progress entry with float conversion`() {
        val rows = repository.findAllProgressPercentsByUser(user1Id)

        assertThat(rows).hasSize(4)
        assertThat(rows.mapNotNull { it.epubProgressPercent }).containsExactly(100.0f)
        assertThat(rows.mapNotNull { it.pdfProgressPercent }).containsExactly(40.0f)
        assertThat(rows.mapNotNull { it.cbxProgressPercent }).containsExactly(75.0f)
        assertThat(rows.mapNotNull { it.koreaderProgressPercent }).containsExactly(55.0f)
        assertThat(rows.mapNotNull { it.koboProgressPercent }).containsExactly(10.0f)
    }

    // =============================================================
    // findExistingProgressBookIds
    // =============================================================

    @Test
    fun `findExistingProgressBookIds returns only books with progress for the user`() {
        val ids = repository.findExistingProgressBookIds(user1Id, listOf(book1Id, book3Id, 999_999L))

        assertThat(ids).containsExactlyInAnyOrder(book1Id, book3Id)
    }

    // =============================================================
    // Bulk writes
    // =============================================================

    @Test
    fun `bulkUpdateReadStatus updates status, modified time and dateFinished for the user only`() {
        val modifiedTime = Instant.parse("2026-06-01T10:00:00Z")
        val dateFinished = Instant.parse("2026-05-31T00:00:00Z")

        val updated = repository.bulkUpdateReadStatus(
            user1Id, listOf(book1Id, book2Id), ReadStatus.READ, modifiedTime, dateFinished
        )

        assertThat(updated).isEqualTo(2)
        val row = fetchProgress(user1Id, book2Id)
        assertThat(row[ubp.READ_STATUS]).isEqualTo("READ")
        assertThat(row[ubp.READ_STATUS_MODIFIED_TIME]).isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 0))
        assertThat(row[ubp.DATE_FINISHED]).isEqualTo(LocalDateTime.of(2026, 5, 31, 0, 0))
        // user2's row untouched
        assertThat(fetchProgress(user2Id, book1Id)[ubp.PERSONAL_RATING]).isEqualTo(4.toByte())
        assertThat(fetchProgress(user2Id, book1Id)[ubp.DATE_FINISHED]).isEqualTo(LocalDateTime.of(2026, 3, 20, 10, 0))
    }

    @Test
    fun `bulkUpdateReadStatus can clear status and dateFinished`() {
        repository.bulkUpdateReadStatus(user1Id, listOf(book1Id), null, Instant.now(), null)

        val row = fetchProgress(user1Id, book1Id)
        assertThat(row[ubp.READ_STATUS]).isNull()
        assertThat(row[ubp.DATE_FINISHED]).isNull()
    }

    @Test
    fun `bulkResetBookloreProgress clears booklore fields but keeps koreader and kobo state`() {
        val modifiedTime = Instant.parse("2026-06-01T10:00:00Z")

        val updated = repository.bulkResetBookloreProgress(user1Id, listOf(book1Id), modifiedTime)

        assertThat(updated).isEqualTo(1)
        val row = fetchProgress(user1Id, book1Id)
        assertThat(row[ubp.READ_STATUS]).isNull()
        assertThat(row[ubp.LAST_READ_TIME]).isNull()
        assertThat(row[ubp.DATE_FINISHED]).isNull()
        assertThat(row[ubp.EPUB_PROGRESS_PERCENT] as Double?).isNull()
        assertThat(row[ubp.READ_STATUS_MODIFIED_TIME]).isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 0))
        // other sync sources untouched
        assertThat(row[ubp.KOREADER_PROGRESS_PERCENT]).isEqualTo(55.0)
        assertThat(row[ubp.KOBO_PROGRESS_PERCENT]).isEqualTo(10.0)
    }

    @Test
    fun `bulkResetKoreaderProgress clears only koreader fields`() {
        val updated = repository.bulkResetKoreaderProgress(user1Id, listOf(book1Id))

        assertThat(updated).isEqualTo(1)
        val row = fetchProgress(user1Id, book1Id)
        assertThat(row[ubp.KOREADER_PROGRESS]).isNull()
        assertThat(row[ubp.KOREADER_PROGRESS_PERCENT] as Double?).isNull()
        assertThat(row[ubp.KOREADER_DEVICE]).isNull()
        assertThat(row[ubp.KOREADER_DEVICE_ID]).isNull()
        assertThat(row[ubp.KOREADER_LAST_SYNC_TIME]).isNull()
        assertThat(row[ubp.EPUB_PROGRESS_PERCENT]).isEqualTo(100.0)
        assertThat(row[ubp.KOBO_PROGRESS_PERCENT]).isEqualTo(10.0)
    }

    @Test
    fun `bulkResetKoboProgress clears only kobo fields`() {
        val updated = repository.bulkResetKoboProgress(user1Id, listOf(book1Id))

        assertThat(updated).isEqualTo(1)
        val row = fetchProgress(user1Id, book1Id)
        assertThat(row[ubp.KOBO_PROGRESS_PERCENT] as Double?).isNull()
        assertThat(row[ubp.KOBO_LOCATION]).isNull()
        assertThat(row[ubp.KOBO_LOCATION_TYPE]).isNull()
        assertThat(row[ubp.KOBO_LOCATION_SOURCE]).isNull()
        assertThat(row[ubp.KOBO_PROGRESS_RECEIVED_TIME]).isNull()
        assertThat(row[ubp.KOREADER_PROGRESS_PERCENT]).isEqualTo(55.0)
    }

    @Test
    fun `bulkUpdatePersonalRating sets and clears ratings`() {
        repository.bulkUpdatePersonalRating(user1Id, listOf(book1Id, book2Id), 2)
        assertThat(fetchProgress(user1Id, book1Id)[ubp.PERSONAL_RATING]).isEqualTo(2.toByte())
        assertThat(fetchProgress(user1Id, book2Id)[ubp.PERSONAL_RATING]).isEqualTo(2.toByte())

        repository.bulkUpdatePersonalRating(user1Id, listOf(book1Id), null)
        assertThat(fetchProgress(user1Id, book1Id)[ubp.PERSONAL_RATING] as Byte?).isNull()
    }

    // =============================================================
    // Helpers
    // =============================================================

    private fun fetchProgress(userId: Long, bookId: Long) =
        dsl.selectFrom(ubp)
            .where(ubp.USER_ID.eq(userId))
            .and(ubp.BOOK_ID.eq(bookId))
            .fetchOne()!!

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID)
            .fetchOne()!!.get(USERS.ID)!!

    private fun insertBook(): Long =
        dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libId)
            .set(BOOK.ADDED_ON, LocalDateTime.now())
            .returningResult(BOOK.ID)
            .fetchOne()!!.get(BOOK.ID)!!

    private fun insertProgress(
        userId: Long,
        bookId: Long,
        readStatus: String? = null,
        rating: Int? = null,
        dateFinished: LocalDateTime? = null,
        readStatusModifiedTime: LocalDateTime? = null,
        lastReadTime: LocalDateTime? = null,
        epubProgressPercent: Double? = null,
        pdfProgressPercent: Double? = null,
        cbxProgressPercent: Double? = null,
        koreaderProgressPercent: Double? = null,
        koboProgressPercent: Double? = null
    ) {
        val insert = dsl.insertInto(ubp)
            .set(ubp.USER_ID, userId)
            .set(ubp.BOOK_ID, bookId)
        if (readStatus != null) insert.set(ubp.READ_STATUS, readStatus)
        if (rating != null) insert.set(ubp.PERSONAL_RATING, rating.toByte())
        if (dateFinished != null) insert.set(ubp.DATE_FINISHED, dateFinished)
        if (readStatusModifiedTime != null) insert.set(ubp.READ_STATUS_MODIFIED_TIME, readStatusModifiedTime)
        if (lastReadTime != null) insert.set(ubp.LAST_READ_TIME, lastReadTime)
        if (epubProgressPercent != null) insert.set(ubp.EPUB_PROGRESS_PERCENT, epubProgressPercent)
        if (pdfProgressPercent != null) insert.set(ubp.PDF_PROGRESS_PERCENT, pdfProgressPercent)
        if (cbxProgressPercent != null) insert.set(ubp.CBX_PROGRESS_PERCENT, cbxProgressPercent)
        if (koreaderProgressPercent != null) {
            insert.set(ubp.KOREADER_PROGRESS, "koreader-pos")
            insert.set(ubp.KOREADER_PROGRESS_PERCENT, koreaderProgressPercent)
            insert.set(ubp.KOREADER_DEVICE, "device")
            insert.set(ubp.KOREADER_DEVICE_ID, "device-id")
            insert.set(ubp.KOREADER_LAST_SYNC_TIME, LocalDateTime.of(2026, 1, 1, 0, 0))
        }
        if (koboProgressPercent != null) {
            insert.set(ubp.KOBO_PROGRESS_PERCENT, koboProgressPercent)
            insert.set(ubp.KOBO_LOCATION, "kobo-loc")
            insert.set(ubp.KOBO_LOCATION_TYPE, "span")
            insert.set(ubp.KOBO_LOCATION_SOURCE, "source")
            insert.set(ubp.KOBO_PROGRESS_RECEIVED_TIME, LocalDateTime.of(2026, 1, 2, 0, 0))
        }
        insert.execute()
    }
}
