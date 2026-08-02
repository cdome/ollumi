package org.booklore.repository.jooq.dto

import org.booklore.model.enums.FetchedMetadataProposalStatus
import java.time.Instant

data class MetadataFetchProposalRow(
    val proposalId: Long,
    val taskId: String,
    val bookId: Long,
    val fetchedAt: Instant?,
    val reviewedAt: Instant?,
    val reviewerUserId: Long?,
    val status: FetchedMetadataProposalStatus,
    val metadataJson: String,
)
