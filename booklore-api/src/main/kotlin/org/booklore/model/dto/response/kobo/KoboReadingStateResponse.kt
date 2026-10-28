package org.booklore.model.dto.response.kobo

import com.fasterxml.jackson.annotation.JsonCreator

data class KoboReadingStateResponse constructor(
        val requestResult: String? = null,
        val updateResults: List<UpdateResult?>? = null
) {
    data class UpdateResult constructor(
            val entitlementId: String? = null,
            val currentBookmarkResult: Result? = null,
            val statisticsResult: Result? = null,
            val statusInfoResult: Result? = null
    )

    data class Result constructor(
            val result: String? = null
    ) {
        companion object {
            @JvmStatic
            fun success(): Result = Result(result = "Success")
        }
    }
}
