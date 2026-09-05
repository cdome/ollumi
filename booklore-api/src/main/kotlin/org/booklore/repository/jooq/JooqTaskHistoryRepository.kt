package org.booklore.repository.jooq

import org.booklore.jooq.tables.Tasks.TASKS
import org.booklore.jooq.tables.records.TasksRecord
import org.booklore.model.enums.TaskType
import org.booklore.repository.jooq.dto.TaskHistory
import org.booklore.task.TaskStatus
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@Repository
class JooqTaskHistoryRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) {

    private val t = TASKS

    fun insert(
        id: String,
        type: TaskType?,
        status: TaskStatus,
        userId: Long,
        createdAt: LocalDateTime,
        progressPercentage: Int,
        taskOptions: Map<String, Any?>?,
    ) {
        dsl.insertInto(t)
            .set(t.ID, id)
            .set(t.TYPE, type?.name)
            .set(t.STATUS, status.name)
            .set(t.USER_ID, userId)
            .set(t.CREATED_AT, createdAt)
            .set(t.PROGRESS_PERCENTAGE, progressPercentage)
            .set(t.TASK_OPTIONS, taskOptions?.let { objectMapper.writeValueAsString(it) })
            .execute()
    }

    /** Non-terminal status change: touches status/message/updated_at only. No-op if the row is gone. */
    fun updateStatus(id: String, status: TaskStatus, message: String?, updatedAt: LocalDateTime) {
        dsl.update(t)
            .set(t.STATUS, status.name)
            .set(t.MESSAGE, message)
            .set(t.UPDATED_AT, updatedAt)
            .where(t.ID.eq(id))
            .execute()
    }

    /** Terminal status change (COMPLETED/FAILED): also stamps completed_at and progress. */
    fun completeStatus(
        id: String,
        status: TaskStatus,
        message: String?,
        updatedAt: LocalDateTime,
        completedAt: LocalDateTime,
        progressPercentage: Int,
    ) {
        dsl.update(t)
            .set(t.STATUS, status.name)
            .set(t.MESSAGE, message)
            .set(t.UPDATED_AT, updatedAt)
            .set(t.COMPLETED_AT, completedAt)
            .set(t.PROGRESS_PERCENTAGE, progressPercentage)
            .where(t.ID.eq(id))
            .execute()
    }

    fun updateError(id: String, errorDetails: String?, completedAt: LocalDateTime, updatedAt: LocalDateTime) {
        dsl.update(t)
            .set(t.STATUS, TaskStatus.FAILED.name)
            .set(t.ERRORDETAILS, errorDetails)
            .set(t.COMPLETED_AT, completedAt)
            .set(t.UPDATED_AT, updatedAt)
            .where(t.ID.eq(id))
            .execute()
    }

    fun findLatestTaskForEachType(): List<TaskHistory> {
        val t2 = TASKS.`as`("t2")
        return dsl.selectFrom(t)
            .where(
                t.CREATED_AT.eq(
                    DSL.select(DSL.max(t2.CREATED_AT)).from(t2).where(t2.TYPE.eq(t.TYPE))
                )
            )
            .orderBy(t.CREATED_AT.desc())
            .fetch()
            .mapNotNull(::toDomainOrNull)
    }

    /** Skips rows whose stored type no longer maps to a known enum (removed values). */
    private fun toDomainOrNull(r: TasksRecord): TaskHistory? {
        val type = r.type?.let { runCatching { TaskType.valueOf(it) }.getOrNull() } ?: return null
        val status = r.status?.let { runCatching { TaskStatus.valueOf(it) }.getOrNull() }
        return TaskHistory(
            id = r.id,
            type = type,
            status = status,
            progressPercentage = r.get(t.PROGRESS_PERCENTAGE),
            message = r.get(t.MESSAGE),
            createdAt = r.createdAt,
            updatedAt = r.get(t.UPDATED_AT),
            completedAt = r.get(t.COMPLETED_AT),
        )
    }
}
