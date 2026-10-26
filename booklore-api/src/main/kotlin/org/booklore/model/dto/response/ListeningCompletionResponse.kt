package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class ListeningCompletionResponse constructor(
        val totalAudiobooks: Int? = null,
        val completed: Int? = null,
        val inProgressCount: Int? = null,
        val inProgress: List<AudiobookCompletionEntry?>? = null
) {
    data class AudiobookCompletionEntry constructor(
            val bookId: Long? = null,
            val title: String? = null,
            val progressPercent: Double? = null,
            val totalDurationSeconds: Long? = null,
            val listenedDurationSeconds: Long? = null
    )
}
