package org.booklore.model.dto.response

import org.booklore.task.TaskStatus
import org.booklore.model.enums.TaskType
import java.time.Instant
import com.fasterxml.jackson.annotation.JsonCreator

data class TasksHistoryResponse constructor(
        val taskHistories: List<TaskHistory?>? = null
) {
    data class TaskHistory constructor(
            val id: String? = null,
            val type: TaskType? = null,
            val status: TaskStatus? = null,
            val progressPercentage: Int? = null,
            val message: String? = null,
            val createdAt: Instant? = null,
            val updatedAt: Instant? = null,
            val completedAt: Instant? = null
    )
}
