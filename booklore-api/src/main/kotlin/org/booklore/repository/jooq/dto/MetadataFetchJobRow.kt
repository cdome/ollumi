package org.booklore.repository.jooq.dto

import org.booklore.model.enums.MetadataFetchTaskStatus
import java.time.Instant

data class MetadataFetchJobRow(
    val taskId: String,
    val userId: Long?,
    val status: MetadataFetchTaskStatus,
    val statusMessage: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val totalBooksCount: Int?,
    val completedBooks: Int?,
    val proposals: List<MetadataFetchProposalRow>,
)
