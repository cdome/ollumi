package org.booklore.repository.jooq.dto

import java.time.LocalDateTime

data class AuthorFacet(
    val name: String,
    val count: Long
)

data class LanguageFacet(
    val code: String,
    val count: Long
)

/** One row of the app series listing aggregate. */
data class SeriesAggregate(
    val seriesName: String,
    val bookCount: Long,
    val seriesTotal: Int?,
    val latestAddedOn: LocalDateTime?,
    val booksRead: Long
)

/**
 * One row of the app author listing: the author fields plus the count of the
 * user's accessible, non-deleted books with files.
 */
data class AuthorSummaryRow(
    val id: Long,
    val name: String,
    val asin: String?,
    val bookCount: Long
)
