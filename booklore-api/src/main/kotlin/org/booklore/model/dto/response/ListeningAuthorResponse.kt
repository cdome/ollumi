package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class ListeningAuthorResponse constructor(
        val author: String? = null,
        val bookCount: Long? = null,
        val totalSessions: Long? = null,
        val totalDurationSeconds: Long? = null
)
