package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class AudiobookChapter constructor(
        val index: Int? = null,
        val title: String? = null,
        val startTimeMs: Long? = null,
        val endTimeMs: Long? = null,
        val durationMs: Long? = null
)
