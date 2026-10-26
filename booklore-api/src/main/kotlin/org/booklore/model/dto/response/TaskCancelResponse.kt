package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class TaskCancelResponse constructor(
        val taskId: String? = null,
        @get:JvmName("isCancelled")
        @get:JsonProperty("cancelled")
        val cancelled: Boolean = false,
        val message: String? = null
)
