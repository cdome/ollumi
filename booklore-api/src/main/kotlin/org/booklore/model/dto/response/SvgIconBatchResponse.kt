package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class SvgIconBatchResponse constructor(
        val totalRequested: Int? = null,
        val successCount: Int? = null,
        val failureCount: Int? = null,
        val results: List<IconSaveResult?>? = null
) {
    data class IconSaveResult constructor(
            val iconName: String? = null,
            @get:JvmName("isSuccess")
            @get:JsonProperty("success")
            val success: Boolean? = null,
            val errorMessage: String? = null
    )
}
