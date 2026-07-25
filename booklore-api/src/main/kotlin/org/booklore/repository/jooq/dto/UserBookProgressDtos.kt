package org.booklore.repository.jooq.dto

import org.booklore.model.enums.ReadStatus

data class CompletionTimelineEntry(
    val year: Int,
    val month: Int,
    val readStatus: ReadStatus,
    val bookCount: Long
)

data class BookCompletionHeatmapEntry(
    val year: Int,
    val month: Int,
    val count: Long
)

data class RatingDistributionEntry(
    val rating: Int,
    val count: Long
)

data class StatusDistributionEntry(
    val status: ReadStatus,
    val count: Long
)

data class ProgressPercents(
    val koreaderProgressPercent: Float?,
    val koboProgressPercent: Float?,
    val epubProgressPercent: Float?,
    val pdfProgressPercent: Float?,
    val cbxProgressPercent: Float?
)
