package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class BookDistributionsResponse constructor(
        val ratingDistribution: List<RatingBucket?>? = null,
        val progressDistribution: List<ProgressBucket?>? = null,
        val statusDistribution: List<StatusBucket?>? = null
) {
    data class RatingBucket constructor(
            val rating: Int? = null,
            val count: Long? = null
    )

    data class ProgressBucket constructor(
            val range: String? = null,
            val min: Int? = null,
            val max: Int? = null,
            val count: Long? = null
    )

    data class StatusBucket constructor(
            val status: String? = null,
            val count: Long? = null
    )
}
