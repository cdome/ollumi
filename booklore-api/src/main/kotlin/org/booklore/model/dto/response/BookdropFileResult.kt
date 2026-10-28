package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class BookdropFileResult constructor(
        val fileName: String? = null,
        @get:JvmName("isSuccess")
        @get:JsonProperty("success")
        val success: Boolean? = null,
        val message: String? = null
)
