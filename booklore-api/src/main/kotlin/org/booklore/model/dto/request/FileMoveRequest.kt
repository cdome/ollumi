package org.booklore.model.dto.request

data class FileMoveRequest(
    val bookIds: Set<Long>? = null,
    val moves: List<Move>? = null
) {
    data class Move(
        val bookId: Long? = null,
        val targetLibraryId: Long? = null,
        val targetLibraryPathId: Long? = null
    )
}
