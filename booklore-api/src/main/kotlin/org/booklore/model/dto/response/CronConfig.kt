package org.booklore.model.dto.response

import org.booklore.model.enums.TaskType
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonCreator

data class CronConfig constructor(
        val id: Long? = null,
        val taskType: TaskType? = null,
        val cronExpression: String? = null,
        val enabled: Boolean? = null,
        val createdAt: LocalDateTime? = null,
        val updatedAt: LocalDateTime? = null
)
