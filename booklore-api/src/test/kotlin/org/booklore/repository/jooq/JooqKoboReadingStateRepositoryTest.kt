package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.KoboReadingState.KOBO_READING_STATE
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.dto.kobo.KoboReadingState
import org.booklore.model.enums.KoboReadStatus
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqKoboReadingStateRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqKoboReadingStateRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(KOBO_READING_STATE).execute()
        dsl.deleteFrom(USERS).execute()
        userId = insertUser("reader")
    }

    private fun sampleState(entitlementId: String): KoboReadingState =
        KoboReadingState.builder()
            .entitlementId(entitlementId)
            .created("2026-01-01T00:00:00.0000000Z")
            .lastModified("2026-01-02T00:00:00.0000000Z")
            .priorityTimestamp("2026-01-02T00:00:00.0000000Z")
            .statusInfo(
                KoboReadingState.StatusInfo.builder()
                    .lastModified("2026-01-02T00:00:00.0000000Z")
                    .status(KoboReadStatus.READING)
                    .timesStartedReading(3)
                    .build()
            )
            .statistics(
                KoboReadingState.Statistics.builder()
                    .lastModified("2026-01-02T00:00:00.0000000Z")
                    .spentReadingMinutes(42)
                    .remainingTimeMinutes(10)
                    .build()
            )
            .currentBookmark(
                KoboReadingState.CurrentBookmark.builder()
                    .lastModified("2026-01-02T00:00:00.0000000Z")
                    .progressPercent(55)
                    .location(
                        KoboReadingState.CurrentBookmark.Location.builder()
                            .value("/6/4").type("KoboSpan").source("cfi").build()
                    )
                    .build()
            )
            .build()

    @Test
    fun `insert then find round-trips nested json`() {
        repository.insert(userId, sampleState("book-1"))

        val found = repository.findByEntitlementIdAndUserId("book-1", userId)!!
        assertThat(found.entitlementId).isEqualTo("book-1")
        assertThat(found.created).isEqualTo("2026-01-01T00:00:00.0000000Z")
        assertThat(found.lastModified).isEqualTo("2026-01-02T00:00:00.0000000Z")
        assertThat(found.statusInfo.status).isEqualTo(KoboReadStatus.READING)
        assertThat(found.statusInfo.timesStartedReading).isEqualTo(3)
        assertThat(found.statistics.spentReadingMinutes).isEqualTo(42)
        assertThat(found.currentBookmark.progressPercent).isEqualTo(55)
        assertThat(found.currentBookmark.location.value).isEqualTo("/6/4")
    }

    @Test
    fun `find returns null when absent or for a different user`() {
        repository.insert(userId, sampleState("book-1"))
        assertThat(repository.findByEntitlementIdAndUserId("missing", userId)).isNull()
        assertThat(repository.findByEntitlementIdAndUserId("book-1", userId + 999)).isNull()
    }

    @Test
    fun `update replaces json blobs and timestamps but keeps created`() {
        repository.insert(userId, sampleState("book-1"))
        val created = repository.findByEntitlementIdAndUserId("book-1", userId)!!.created

        val merged = KoboReadingState.builder()
            .entitlementId("book-1")
            .lastModified("2026-02-02T00:00:00.0000000Z")
            .priorityTimestamp("2026-02-02T00:00:00.0000000Z")
            .statusInfo(KoboReadingState.StatusInfo.builder().status(KoboReadStatus.FINISHED).build())
            .statistics(KoboReadingState.Statistics.builder().spentReadingMinutes(99).build())
            .currentBookmark(KoboReadingState.CurrentBookmark.builder().progressPercent(100).build())
            .build()
        repository.updateByEntitlementIdAndUserId("book-1", userId, merged)

        val after = repository.findByEntitlementIdAndUserId("book-1", userId)!!
        assertThat(after.created).isEqualTo(created)
        assertThat(after.lastModified).isEqualTo("2026-02-02T00:00:00.0000000Z")
        assertThat(after.statusInfo.status).isEqualTo(KoboReadStatus.FINISHED)
        assertThat(after.statistics.spentReadingMinutes).isEqualTo(99)
        assertThat(after.currentBookmark.progressPercent).isEqualTo(100)
        assertThat(dsl.fetchCount(KOBO_READING_STATE)).isEqualTo(1)
    }

    @Test
    fun `findFirstByEntitlementIdWithNullUser picks the highest-priority null-user row`() {
        // two legacy rows with user_id NULL for the same entitlement, different priority
        insertNullUserRow("book-1", priority = "2026-01-01T00:00:00.0000000Z")
        insertNullUserRow("book-1", priority = "2026-05-01T00:00:00.0000000Z")
        // a user-scoped row that must be ignored by this finder
        repository.insert(userId, sampleState("book-1"))

        val found = repository.findFirstByEntitlementIdWithNullUserOrderByPriority("book-1")!!
        assertThat(found.priorityTimestamp).isEqualTo("2026-05-01T00:00:00.0000000Z")
    }

    @Test
    fun `delete removes the user's row`() {
        repository.insert(userId, sampleState("book-1"))
        repository.deleteByEntitlementIdAndUserId("book-1", userId)
        assertThat(repository.findByEntitlementIdAndUserId("book-1", userId)).isNull()
    }

    private fun insertNullUserRow(entitlementId: String, priority: String) {
        dsl.insertInto(KOBO_READING_STATE)
            .set(KOBO_READING_STATE.ENTITLEMENT_ID, entitlementId)
            .set(KOBO_READING_STATE.PRIORITY_TIMESTAMP, priority)
            .set(KOBO_READING_STATE.LAST_MODIFIED_STRING, priority)
            .execute()
    }

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
}
