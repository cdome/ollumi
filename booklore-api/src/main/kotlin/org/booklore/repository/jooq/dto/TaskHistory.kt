package org.booklore.repository.jooq.dto

import org.booklore.model.enums.TaskType
import org.booklore.task.TaskStatus
import java.time.LocalDateTime

/** Read view of a `tasks` row for the latest-per-type history (replaces TaskHistoryEntity). */
data class TaskHistory(
    val id: String,
    val type: TaskType,
    val status: TaskStatus?,
    val progressPercentage: Int?,
    val message: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
)
