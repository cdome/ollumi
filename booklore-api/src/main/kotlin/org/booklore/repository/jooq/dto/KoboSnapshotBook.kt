package org.booklore.repository.jooq.dto

import java.time.Instant

/** Domain view of a `kobo_library_snapshot_book` row (replaces KoboSnapshotBookEntity). */
data class KoboSnapshotBook(
    val id: Long,
    val snapshotId: String,
    val bookId: Long,
    val fileHash: String?,
    val metadataUpdatedAt: Instant?,
    val synced: Boolean,
)
