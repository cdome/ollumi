package org.booklore.model.dto.response

import org.booklore.model.enums.TaskType
import org.booklore.task.TaskStatus

data class TaskCreateResponse(
    val taskId: String? = null,
    val taskType: TaskType? = null,
    val status: TaskStatus? = null
)
