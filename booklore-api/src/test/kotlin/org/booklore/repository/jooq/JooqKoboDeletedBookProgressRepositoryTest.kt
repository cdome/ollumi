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
import java.time.LocalDateTime

class JooqKoboDeletedBookProgressRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqKoboDeletedBookProgressRepository
    @Autowired private lateinit var snapshotRepository: JooqKoboLibrarySnapshotRepository
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
        // Tracking rows FK-reference a snapshot; seed two owned by the primary user.
        snapshotRepository.insert("snap", userId, LocalDateTime.of(2026, 1, 1, 0, 0, 0), emptyList())
        snapshotRepository.insert("snap2", userId, LocalDateTime.of(2026, 1, 2, 0, 0, 0), emptyList())
    }

    @Test
    fun `insertAll writes one tracking row per book id`() {
        repository.insertAll("snap", userId, listOf(10L, 20L, 30L))

        val ids = dsl.select(KOBO_REMOVED_BOOKS_TRACKING.BOOK_ID_SYNCED)
            .from(KOBO_REMOVED_BOOKS_TRACKING)
            .where(
                KOBO_REMOVED_BOOKS_TRACKING.SNAPSHOT_ID.eq("snap")
                    .and(KOBO_REMOVED_BOOKS_TRACKING.USER_ID.eq(userId))
            )
            .fetch(KOBO_REMOVED_BOOKS_TRACKING.BOOK_ID_SYNCED)

        assertThat(ids).containsExactlyInAnyOrder(10L, 20L, 30L)
    }

    @Test
    fun `insertAll with an empty list is a no-op`() {
        repository.insertAll("snap", userId, emptyList())

        assertThat(dsl.fetchCount(KOBO_REMOVED_BOOKS_TRACKING)).isEqualTo(0)
    }

    @Test
    fun `deleteBySnapshotIdAndUserId removes only the matching snapshot and user rows`() {
        repository.insertAll("snap", userId, listOf(10L, 20L))
        repository.insertAll("snap", otherUserId, listOf(30L))
        repository.insertAll("snap2", userId, listOf(40L))

        repository.deleteBySnapshotIdAndUserId("snap", userId)

        assertThat(countFor("snap", userId)).isEqualTo(0)
        assertThat(countFor("snap", otherUserId)).isEqualTo(1)
        assertThat(countFor("snap2", userId)).isEqualTo(1)
        assertThat(dsl.fetchCount(KOBO_REMOVED_BOOKS_TRACKING)).isEqualTo(2)
    }

    private fun countFor(snapshotId: String, uid: Long): Int =
        dsl.fetchCount(
            KOBO_REMOVED_BOOKS_TRACKING,
            KOBO_REMOVED_BOOKS_TRACKING.SNAPSHOT_ID.eq(snapshotId)
                .and(KOBO_REMOVED_BOOKS_TRACKING.USER_ID.eq(uid)),
        )

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
