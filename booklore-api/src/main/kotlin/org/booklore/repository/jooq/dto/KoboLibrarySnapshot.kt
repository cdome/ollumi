package org.booklore.repository.jooq.dto

import java.time.LocalDateTime

/** Domain view of a `kobo_library_snapshot` row (replaces KoboLibrarySnapshotEntity). */
data class KoboLibrarySnapshot(
    val id: String,
    val userId: Long,
    val createdDate: LocalDateTime,
)
