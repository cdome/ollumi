package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.KoboLibrarySnapshot.KOBO_LIBRARY_SNAPSHOT
import org.booklore.jooq.tables.KoboLibrarySnapshotBook.KOBO_LIBRARY_SNAPSHOT_BOOK
import org.booklore.jooq.tables.KoboRemovedBooksTracking.KOBO_REMOVED_BOOKS_TRACKING
import org.booklore.jooq.tables.Users.USERS
import org.booklore.repository.jooq.dto.KoboSnapshotBook
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDateTime

class JooqKoboLibrarySnapshotRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqKoboLibrarySnapshotRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var otherUserId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(KOBO_REMOVED_BOOKS_TRACKING).execute()
        dsl.deleteFrom(KOBO_LIBRARY_SNAPSHOT_BOOK).execute()
        dsl.deleteFrom(KOBO_LIBRARY_SNAPSHOT).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("owner")
        otherUserId = insertUser("other")
    }

    @Test
    fun `insert persists the snapshot row and its snapshot-book children`() {
        val createdDate = LocalDateTime.of(2026, 1, 1, 12, 0, 0)
        val books = listOf(
            KoboSnapshotBook(0L, "ignored", 101L, "hashA", Instant.parse("2026-01-01T10:15:30Z"), false),
            KoboSnapshotBook(0L, "ignored", 202L, null, null, true),
        )

        val result = repository.insert("snap-1", userId, createdDate, books)

        assertThat(result.id).isEqualTo("snap-1")
        assertThat(result.userId).isEqualTo(userId)
        assertThat(result.createdDate).isEqualTo(createdDate)

        // The snapshot row is persisted and findable.
        assertThat(repository.findByIdAndUserId("snap-1", userId)).isNotNull

        // The children are persisted with the snapshot id (not the placeholder in the input records).
        val rows = dsl.selectFrom(KOBO_LIBRARY_SNAPSHOT_BOOK)
            .where(KOBO_LIBRARY_SNAPSHOT_BOOK.SNAPSHOT_ID.eq("snap-1"))
            .orderBy(KOBO_LIBRARY_SNAPSHOT_BOOK.BOOK_ID)
            .fetch()

        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.bookId }).containsExactly(101L, 202L)
        assertThat(rows[0].synced).isEqualTo(0.toByte())
        assertThat(rows[0].fileHash).isEqualTo("hashA")
        assertThat(rows[0].metadataUpdatedAt).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 15, 30))
        assertThat(rows[1].synced).isEqualTo(1.toByte())
        assertThat(rows[1].fileHash).isNull()
        assertThat(rows[1].metadataUpdatedAt).isNull()
    }

    @Test
    fun `insert with no books persists just the snapshot row`() {
        val createdDate = LocalDateTime.of(2026, 2, 2, 8, 30, 0)

        repository.insert("snap-empty", userId, createdDate, emptyList())

        assertThat(repository.findByIdAndUserId("snap-empty", userId)).isNotNull
        assertThat(
            dsl.fetchCount(
                KOBO_LIBRARY_SNAPSHOT_BOOK,
                KOBO_LIBRARY_SNAPSHOT_BOOK.SNAPSHOT_ID.eq("snap-empty"),
            )
        ).isEqualTo(0)
    }

    @Test
    fun `findByIdAndUserId round-trips the snapshot when present`() {
        val createdDate = LocalDateTime.of(2026, 3, 3, 9, 0, 0)
        repository.insert("snap-1", userId, createdDate, emptyList())

        val found = repository.findByIdAndUserId("snap-1", userId)!!
        assertThat(found.id).isEqualTo("snap-1")
        assertThat(found.userId).isEqualTo(userId)
        assertThat(found.createdDate).isEqualTo(createdDate)
    }

    @Test
    fun `findByIdAndUserId returns null when absent or owned by a different user`() {
        repository.insert("snap-1", userId, LocalDateTime.of(2026, 1, 1, 12, 0, 0), emptyList())

        assertThat(repository.findByIdAndUserId("missing", userId)).isNull()
        assertThat(repository.findByIdAndUserId("snap-1", otherUserId)).isNull()
    }

    @Test
    fun `deleteById removes the snapshot and cascades its snapshot-book children`() {
        val books = listOf(
            KoboSnapshotBook(0L, "ignored", 101L, "hashA", null, false),
            KoboSnapshotBook(0L, "ignored", 202L, "hashB", null, false),
        )
        repository.insert("snap-1", userId, LocalDateTime.of(2026, 1, 1, 12, 0, 0), books)
        assertThat(dsl.fetchCount(KOBO_LIBRARY_SNAPSHOT_BOOK, KOBO_LIBRARY_SNAPSHOT_BOOK.SNAPSHOT_ID.eq("snap-1")))
            .isEqualTo(2)

        repository.deleteById("snap-1")

        assertThat(repository.findByIdAndUserId("snap-1", userId)).isNull()
        // Children are gone via the DB ON DELETE CASCADE FK.
        assertThat(dsl.fetchCount(KOBO_LIBRARY_SNAPSHOT_BOOK, KOBO_LIBRARY_SNAPSHOT_BOOK.SNAPSHOT_ID.eq("snap-1")))
            .isEqualTo(0)
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
