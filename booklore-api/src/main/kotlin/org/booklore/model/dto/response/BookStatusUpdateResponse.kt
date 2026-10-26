package org.booklore.model.dto.response

import org.booklore.model.enums.ReadStatus
import java.time.Instant
import com.fasterxml.jackson.annotation.JsonCreator

data class BookStatusUpdateResponse constructor(
        val bookId: Long? = null,
        val readStatus: ReadStatus? = null,
        val readStatusModifiedTime: Instant? = null,
        val dateFinished: Instant? = null
)
