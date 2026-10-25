package org.booklore.repository.jooq.dto

import java.time.Instant

data class BookCoverUpdate(
    val id: Long,
    val coverUpdatedOn: Instant?
)
