package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class MonthlyPaceResponse constructor(
        val year: Int? = null,
        val month: Int? = null,
        val booksCompleted: Long? = null,
        val totalListeningSeconds: Long? = null
)
