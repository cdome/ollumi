package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class AudiobookInfo constructor(
        val bookId: Long? = null,
        val bookFileId: Long? = null,
        val title: String? = null,
        val author: String? = null,
        val narrator: String? = null,
        val durationMs: Long? = null,
        val bitrate: Int? = null,
        val codec: String? = null,
        val sampleRate: Int? = null,
        val channels: Int? = null,
        val totalSizeBytes: Long? = null,
        @get:JvmName("isFolderBased")
        @get:JsonProperty("folderBased")
        val folderBased: Boolean? = null,
        val chapters: List<AudiobookChapter?>? = null,
        val tracks: List<AudiobookTrack?>? = null
)
