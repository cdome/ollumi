package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.KoboLibrarySnapshot.KOBO_LIBRARY_SNAPSHOT
import org.booklore.jooq.tables.KoboLibrarySnapshotBook.KOBO_LIBRARY_SNAPSHOT_BOOK
import org.booklore.jooq.tables.KoboRemovedBooksTracking.KOBO_REMOVED_BOOKS_TRACKING
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime

class JooqKoboSnapshotBookRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqKoboSnapshotBookRepository
    @Autowired private lateinit var snapshotRepository: JooqKoboLibrarySnapshotRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0

    private val t1: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0, 0)
    private val t2: LocalDateTime = LocalDateTime.of(2026, 2, 2, 0, 0, 0)
    private val page = PageRequest.of(0, 50)

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(KOBO_REMOVED_BOOKS_TRACKING).execute()
        dsl.deleteFrom(KOBO_LIBRARY_SNAPSHOT_BOOK).execute()
        dsl.deleteFrom(KOBO_LIBRARY_SNAPSHOT).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
        // Two snapshots owned by the seeded user; child rows are added per-test.
        snapshotRepository.insert("prev", userId, LocalDateTime.of(2026, 1, 1, 0, 0, 0), emptyList())
        snapshotRepository.insert("curr", userId, LocalDateTime.of(2026, 1, 2, 0, 0, 0), emptyList())
    }

    // 1. findBySnapshotIdAndSyncedFalse: rows WHERE snapshot_id = snapshotId AND synced = false.
    @Test
    fun `findBySnapshotIdAndSyncedFalse returns only the unsynced rows of that snapshot`() {
        addBook("curr", 1L, "h", null, synced = false)
        addBook("curr", 2L, "h", null, synced = true)
        addBook("curr", 3L, "h", null, synced = false)
        // An unsynced row in a different snapshot must be excluded by the snapshot scoping.
        addBook("prev", 1L, "h", null, synced = false)

        val result = repository.findBySnapshotIdAndSyncedFalse("curr", page)

        assertThat(result.content.map { it.bookId }).containsExactlyInAnyOrder(1L, 3L)
        assertThat(result.totalElements).isEqualTo(2L)
    }

    // 2. markBooksSynced: UPDATE synced = true WHERE snapshot_id = snapshotId AND book_id IN bookIds.
    @Test
    fun `markBooksSynced flips synced only for the given books within the snapshot`() {
        addBook("curr", 1L, "h", null, synced = false)
        addBook("curr", 2L, "h", null, synced = false)
        addBook("curr", 3L, "h", null, synced = false)
        // Same book id in another snapshot must not be touched (snapshot scoping).
        addBook("prev", 1L, "h", null, synced = false)

        repository.markBooksSynced("curr", listOf(1L, 2L))

        assertThat(repository.findBySnapshotIdAndSyncedFalse("curr", page).content.map { it.bookId })
            .containsExactly(3L)
        assertThat(syncedOf("prev", 1L)).isEqualTo(0.toByte())
    }

    // 3. findNewlyAddedBooks: curr rows whose book_id is NOT in prev; unsyncedOnly adds synced=false.
    @Test
    fun `findNewlyAddedBooks returns curr books absent from prev, honouring unsyncedOnly`() {
        addBook("prev", 1L, "h", null, synced = false)
        addBook("prev", 2L, "h", null, synced = false)
        addBook("curr", 2L, "h", null, synced = false) // present in prev -> not "new"
        addBook("curr", 3L, "h", null, synced = false) // new + unsynced
        addBook("curr", 4L, "h", null, synced = true)  // new + already synced

        val all = repository.findNewlyAddedBooks("prev", "curr", unsyncedOnly = false, page)
        assertThat(all.content.map { it.bookId }).containsExactlyInAnyOrder(3L, 4L)

        val unsyncedOnly = repository.findNewlyAddedBooks("prev", "curr", unsyncedOnly = true, page)
        assertThat(unsyncedOnly.content.map { it.bookId }).containsExactly(3L)
    }

    // 4. findRemovedBooks: prev books absent from curr AND not tracked as removed for curr.
    @Test
    fun `findRemovedBooks returns prev books absent from curr excluding removal-tracked ones`() {
        addBook("prev", 1L, "h", null, synced = false)
        addBook("prev", 2L, "h", null, synced = false)
        addBook("prev", 3L, "h", null, synced = false)
        addBook("curr", 1L, "h", null, synced = false) // book 1 still present -> not removed

        // book 3 already recorded as removed for the current snapshot -> must be excluded.
        dsl.insertInto(KOBO_REMOVED_BOOKS_TRACKING)
            .set(KOBO_REMOVED_BOOKS_TRACKING.SNAPSHOT_ID, "curr")
            .set(KOBO_REMOVED_BOOKS_TRACKING.USER_ID, userId)
            .set(KOBO_REMOVED_BOOKS_TRACKING.BOOK_ID_SYNCED, 3L)
            .execute()

        val result = repository.findRemovedBooks("prev", "curr", page)

        assertThat(result.content.map { it.bookId }).containsExactly(2L)
    }

    // 5. findUnchangedBooksBetweenSnapshots: same book_id, equal file_hash, and equal-or-both-null metadata.
    @Test
    fun `findUnchangedBooksBetweenSnapshots matches equal hash and equal-or-both-null metadata, returning curr rows`() {
        // unchanged: equal hash, equal metadata
        addBook("prev", 1L, "h1", t1, synced = false)
        addBook("curr", 1L, "h1", t1, synced = false)
        // unchanged: equal hash, metadata null on both sides
        addBook("prev", 2L, "h2", null, synced = false)
        addBook("curr", 2L, "h2", null, synced = false)
        // changed: hash differs
        addBook("prev", 3L, "h3", t1, synced = false)
        addBook("curr", 3L, "hX", t1, synced = false)
        // changed: metadata differs
        addBook("prev", 4L, "h4", t1, synced = false)
        addBook("curr", 4L, "h4", t2, synced = false)
        // only in curr -> no prev row to join
        addBook("curr", 5L, "h5", t1, synced = false)

        val result = repository.findUnchangedBooksBetweenSnapshots("prev", "curr")

        assertThat(result.map { it.bookId }).containsExactlyInAnyOrder(1L, 2L)
        assertThat(result.map { it.snapshotId }).allMatch { it == "curr" }
    }

    // 6. findChangedBooks: unsynced curr rows where hash differs, OR metadata differs (both non-null),
    //    OR curr metadata non-null while prev metadata null.
    @Test
    fun `findChangedBooks matches each change condition on unsynced curr rows only`() {
        // hash differs
        addBook("prev", 1L, "h1", t1, synced = false)
        addBook("curr", 1L, "hX", t1, synced = false)
        // metadata differs, both non-null
        addBook("prev", 2L, "h2", t1, synced = false)
        addBook("curr", 2L, "h2", t2, synced = false)
        // curr metadata non-null, prev metadata null
        addBook("prev", 3L, "h3", null, synced = false)
        addBook("curr", 3L, "h3", t1, synced = false)
        // unchanged -> excluded
        addBook("prev", 4L, "h4", t1, synced = false)
        addBook("curr", 4L, "h4", t1, synced = false)
        // hash differs but already synced -> excluded by synced=false filter
        addBook("prev", 5L, "h5", t1, synced = false)
        addBook("curr", 5L, "hZ", t1, synced = true)
        // metadata cleared (curr null, prev non-null) is NOT treated as changed by this query
        addBook("prev", 6L, "h6", t1, synced = false)
        addBook("curr", 6L, "h6", null, synced = false)

        val result = repository.findChangedBooks("prev", "curr", page)

        assertThat(result.content.map { it.bookId }).containsExactlyInAnyOrder(1L, 2L, 3L)
        assertThat(result.content.map { it.snapshotId }).allMatch { it == "curr" }
        assertThat(result.content).allMatch { !it.synced }
    }

    private fun addBook(snapshotId: String, bookId: Long, fileHash: String?, metadata: LocalDateTime?, synced: Boolean) {
        dsl.insertInto(KOBO_LIBRARY_SNAPSHOT_BOOK)
            .set(KOBO_LIBRARY_SNAPSHOT_BOOK.SNAPSHOT_ID, snapshotId)
            .set(KOBO_LIBRARY_SNAPSHOT_BOOK.BOOK_ID, bookId)
            .set(KOBO_LIBRARY_SNAPSHOT_BOOK.FILE_HASH, fileHash)
            .set(KOBO_LIBRARY_SNAPSHOT_BOOK.METADATA_UPDATED_AT, metadata)
            .set(KOBO_LIBRARY_SNAPSHOT_BOOK.SYNCED, if (synced) 1.toByte() else 0.toByte())
            .execute()
    }

    private fun syncedOf(snapshotId: String, bookId: Long): Byte? =
        dsl.select(KOBO_LIBRARY_SNAPSHOT_BOOK.SYNCED)
            .from(KOBO_LIBRARY_SNAPSHOT_BOOK)
            .where(
                KOBO_LIBRARY_SNAPSHOT_BOOK.SNAPSHOT_ID.eq(snapshotId)
                    .and(KOBO_LIBRARY_SNAPSHOT_BOOK.BOOK_ID.eq(bookId))
            )
            .fetchOne(KOBO_LIBRARY_SNAPSHOT_BOOK.SYNCED)

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
