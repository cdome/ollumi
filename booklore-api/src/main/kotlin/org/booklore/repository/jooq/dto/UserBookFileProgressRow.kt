package org.booklore.repository.jooq.dto

import org.booklore.model.enums.BookFileType
import java.time.Instant

/**
 * Mutable carrier for user_book_file_progress rows. Consumers do find-or-new -> set a subset of
 * fields -> save(), so this mirrors the old entity's managed-mutation shape (no Hibernate/session).
 * bookId/bookType are read-only projections joined from book_file and are ignored by save().
 */
data class UserBookFileProgressRow @JvmOverloads constructor(
    var id: Long? = null,
    var userId: Long? = null,
    var bookFileId: Long? = null,
    var bookId: Long? = null,
    var bookType: BookFileType? = null,
    var positionData: String? = null,
    var positionHref: String? = null,
    var progressPercent: Float? = null,
    var ttsPositionCfi: String? = null,
    var lastReadTime: Instant? = null,
)
