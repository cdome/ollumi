package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class PersonalRatingUpdateResponse constructor(
        val bookId: Long? = null,
        val personalRating: Int? = null
)
