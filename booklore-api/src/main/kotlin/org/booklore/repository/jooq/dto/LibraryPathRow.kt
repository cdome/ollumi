package org.booklore.repository.jooq.dto

/** A row of library_path, read without touching the LAZY LibraryEntity.libraryPaths collection. */
data class LibraryPathRow(
    val id: Long,
    val path: String,
)
