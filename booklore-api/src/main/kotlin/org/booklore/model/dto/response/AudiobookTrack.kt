package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class AudiobookTrack constructor(
        val index: Int? = null,
        val fileName: String? = null,
        val title: String? = null,
        val durationMs: Long? = null,
        val fileSizeBytes: Long? = null,
        val cumulativeStartMs: Long? = null
)
