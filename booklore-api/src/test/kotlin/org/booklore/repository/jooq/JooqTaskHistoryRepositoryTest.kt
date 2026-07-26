package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Tasks.TASKS
import org.booklore.model.enums.TaskType
import org.booklore.task.TaskStatus
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class JooqTaskHistoryRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repository: JooqTaskHistoryRepository

    @Autowired
    private lateinit var dsl: DSLContext

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(TASKS).execute()
    }

    @Test
    fun `insert persists fields and serializes options to json`() {
        val createdAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        repository.insert("t1", TaskType.SYNC_LIBRARY_FILES, TaskStatus.ACCEPTED, 9L, createdAt, 0, mapOf("libraryId" to 5))

        val row = dsl.selectFrom(TASKS).where(TASKS.ID.eq("t1")).fetchOne()!!
        assertThat(row.type).isEqualTo(TaskType.SYNC_LIBRARY_FILES.name)
        assertThat(row.status).isEqualTo(TaskStatus.ACCEPTED.name)
        assertThat(row.userId).isEqualTo(9L)
        assertThat(row.createdAt).isEqualTo(createdAt)
        assertThat(row.progressPercentage).isEqualTo(0)
        assertThat(row.taskOptions).contains("libraryId").contains("5")
    }

    @Test
    fun `insert allows null options`() {
        repository.insert("t2", TaskType.SYNC_LIBRARY_FILES, TaskStatus.ACCEPTED, 9L, LocalDateTime.now(), 0, null)

        val row = dsl.selectFrom(TASKS).where(TASKS.ID.eq("t2")).fetchOne()!!
        assertThat(row.get(TASKS.TASK_OPTIONS) as String?).isNull()
    }

    @Test
    fun `updateStatus touches only status message and updatedAt`() {
        val createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        repository.insert("t3", TaskType.SYNC_LIBRARY_FILES, TaskStatus.ACCEPTED, 1L, createdAt, 0, null)

        repository.updateStatus("t3", TaskStatus.IN_PROGRESS, "Working", LocalDateTime.of(2026, 1, 1, 1, 0))

        val row = dsl.selectFrom(TASKS).where(TASKS.ID.eq("t3")).fetchOne()!!
        assertThat(row.status).isEqualTo(TaskStatus.IN_PROGRESS.name)
        assertThat(row.message).isEqualTo("Working")
        assertThat(row.updatedAt).isEqualTo(LocalDateTime.of(2026, 1, 1, 1, 0))
        assertThat(row.get(TASKS.COMPLETED_AT) as LocalDateTime?).isNull()
        assertThat(row.progressPercentage).isEqualTo(0)
    }

    @Test
    fun `completeStatus stamps completedAt and progress`() {
        repository.insert("t4", TaskType.SYNC_LIBRARY_FILES, TaskStatus.ACCEPTED, 1L, LocalDateTime.now(), 0, null)
        val now = LocalDateTime.of(2026, 2, 2, 2, 2)

        repository.completeStatus("t4", TaskStatus.COMPLETED, "Done", now, now, 100)

        val row = dsl.selectFrom(TASKS).where(TASKS.ID.eq("t4")).fetchOne()!!
        assertThat(row.status).isEqualTo(TaskStatus.COMPLETED.name)
        assertThat(row.completedAt).isEqualTo(now)
        assertThat(row.progressPercentage).isEqualTo(100)
    }

    @Test
    fun `updateError sets failed status and error details`() {
        repository.insert("t5", TaskType.SYNC_LIBRARY_FILES, TaskStatus.ACCEPTED, 1L, LocalDateTime.now(), 0, null)
        val now = LocalDateTime.of(2026, 3, 3, 3, 3)

        repository.updateError("t5", "boom", now, now)

        val row = dsl.selectFrom(TASKS).where(TASKS.ID.eq("t5")).fetchOne()!!
        assertThat(row.status).isEqualTo(TaskStatus.FAILED.name)
        assertThat(row.errordetails).isEqualTo("boom")
        assertThat(row.completedAt).isEqualTo(now)
    }

    @Test
    fun `update methods no-op on missing id`() {
        repository.updateStatus("ghost", TaskStatus.IN_PROGRESS, "x", LocalDateTime.now())
        repository.updateError("ghost", "x", LocalDateTime.now(), LocalDateTime.now())

        assertThat(dsl.fetchCount(TASKS)).isZero()
    }

    @Test
    fun `findLatestTaskForEachType returns most recent per type and skips unknown types`() {
        val base = LocalDateTime.of(2026, 1, 1, 0, 0)
        repository.insert("old", TaskType.SYNC_LIBRARY_FILES, TaskStatus.COMPLETED, 1L, base, 100, null)
        repository.insert("new", TaskType.SYNC_LIBRARY_FILES, TaskStatus.ACCEPTED, 1L, base.plusHours(1), 10, null)
        repository.insert("other", TaskType.CLEANUP_TEMP_METADATA, TaskStatus.COMPLETED, 1L, base.plusHours(2), 100, null)

        // A row whose stored type is no longer a known enum value must be skipped.
        dsl.insertInto(TASKS)
            .set(TASKS.ID, "bogus")
            .set(TASKS.TYPE, "REMOVED_TASK_TYPE_XYZ")
            .set(TASKS.STATUS, TaskStatus.COMPLETED.name)
            .set(TASKS.USER_ID, 1L)
            .set(TASKS.CREATED_AT, base.plusHours(3))
            .execute()

        val latest = repository.findLatestTaskForEachType()

        assertThat(latest).extracting("id").containsExactlyInAnyOrder("new", "other")
        assertThat(latest.first { it.type == TaskType.SYNC_LIBRARY_FILES }.status).isEqualTo(TaskStatus.ACCEPTED)
    }
}
