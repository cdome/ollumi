package org.booklore.model.dto

import org.booklore.model.enums.FetchedMetadataProposalStatus
import java.time.Instant

data class FetchedProposal(
    val proposalId: Long? = null,
    val taskId: String? = null,
    val bookId: Long? = null,
    val fetchedAt: Instant? = null,
    val reviewedAt: Instant? = null,
    val reviewerUserId: String? = null,
    val status: FetchedMetadataProposalStatus? = null,
    val metadataJson: BookMetadata? = null
)
