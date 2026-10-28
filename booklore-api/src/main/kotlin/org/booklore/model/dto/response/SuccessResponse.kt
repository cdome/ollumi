package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonCreator

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SuccessResponse<T> @JvmOverloads constructor(
        val status: Int,
        val message: String,
        val data: T? = null,
        val timestamp: LocalDateTime = LocalDateTime.now()
)
