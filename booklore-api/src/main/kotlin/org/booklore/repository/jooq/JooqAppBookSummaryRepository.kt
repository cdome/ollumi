package org.booklore.repository.jooq

import org.booklore.app.dto.AppBookSummary
import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * jOOQ read model for [AppBookSummary]: projects a set of book IDs straight to
 * summary DTOs, replacing the JPA @EntityGraph load + MapStruct mapping +
 * separate progress fetch. Multi-valued fields (authors, files) are fetched as
 * two batched grouping queries rather than JPA lazy collections. The "primary
 * file" and "read progress" derivations mirror BookEntity / AppBookMapper.
 */
@Repository
class JooqAppBookSummaryRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) {

    private val bm = BOOK_METADATA
    private val ubp = USER_BOOK_PROGRESS
    private val bam = BOOK_METADATA_AUTHOR_MAPPING

    private data class FileCandidate(val bookType: String?, val isBook: Boolean)

    fun findSummariesByIds(bookIds: Collection<Long>, userId: Long?): List<AppBookSummary> {
        if (bookIds.isEmpty()) return emptyList()

        val authorsByBook: Map<Long, List<String>> = dsl
            .select(bam.BOOK_ID, AUTHOR.NAME)
            .from(bam)
            .join(AUTHOR).on(AUTHOR.ID.eq(bam.AUTHOR_ID))
            .where(bam.BOOK_ID.`in`(bookIds))
            .orderBy(bam.BOOK_ID, bam.SORT_ORDER)
            .fetchGroups(bam.BOOK_ID, AUTHOR.NAME)

        val filesByBook: Map<Long, List<FileCandidate>> = dsl
            .select(BOOK_FILE.BOOK_ID, BOOK_FILE.BOOK_TYPE, BOOK_FILE.IS_BOOK)
            .from(BOOK_FILE)
            .where(BOOK_FILE.BOOK_ID.`in`(bookIds))
            .orderBy(BOOK_FILE.BOOK_ID, BOOK_FILE.ID)
            .fetchGroups(BOOK_FILE.BOOK_ID) { r ->
                FileCandidate(r[BOOK_FILE.BOOK_TYPE], r[BOOK_FILE.IS_BOOK] == 1.toByte())
            }

        return dsl.select(
            BOOK.ID, BOOK.ADDED_ON, BOOK.IS_PHYSICAL, BOOK.LIBRARY_ID,
            bm.TITLE, bm.SERIES_NAME, bm.SERIES_NUMBER, bm.COVER_UPDATED_ON, bm.AUDIOBOOK_COVER_UPDATED_ON,
            LIBRARY.FORMAT_PRIORITY,
            ubp.READ_STATUS, ubp.PERSONAL_RATING, ubp.LAST_READ_TIME,
            ubp.KOREADER_PROGRESS_PERCENT, ubp.KOBO_PROGRESS_PERCENT, ubp.EPUB_PROGRESS_PERCENT,
            ubp.PDF_PROGRESS_PERCENT, ubp.CBX_PROGRESS_PERCENT
        )
            .from(BOOK)
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .leftJoin(LIBRARY).on(LIBRARY.ID.eq(BOOK.LIBRARY_ID))
            .leftJoin(ubp).on(ubp.BOOK_ID.eq(BOOK.ID).and(userProgressFilter(userId)))
            .where(BOOK.ID.`in`(bookIds))
            .fetch { record ->
                val bookId = record[BOOK.ID]!!
                AppBookSummary.builder()
                    .id(bookId)
                    .title(record[bm.TITLE])
                    .authors(authorsByBook[bookId] ?: emptyList())
                    .thumbnailUrl("/api/books/$bookId/cover")
                    .readStatus(record[ubp.READ_STATUS])
                    .personalRating(record[ubp.PERSONAL_RATING]?.toInt())
                    .seriesName(record[bm.SERIES_NAME])
                    .seriesNumber(record[bm.SERIES_NUMBER]?.toFloat())
                    .libraryId(record[BOOK.LIBRARY_ID])
                    .addedOn(record[BOOK.ADDED_ON]?.toInstant())
                    .lastReadTime(record[ubp.LAST_READ_TIME]?.toInstant())
                    .readProgress(
                        readProgress(
                            record[ubp.KOREADER_PROGRESS_PERCENT], record[ubp.KOBO_PROGRESS_PERCENT],
                            record[ubp.EPUB_PROGRESS_PERCENT], record[ubp.PDF_PROGRESS_PERCENT],
                            record[ubp.CBX_PROGRESS_PERCENT]
                        )
                    )
                    .primaryFileType(primaryFileType(record[LIBRARY.FORMAT_PRIORITY], filesByBook[bookId].orEmpty()))
                    .coverUpdatedOn(record[bm.COVER_UPDATED_ON]?.toInstant())
                    .audiobookCoverUpdatedOn(record[bm.AUDIOBOOK_COVER_UPDATED_ON]?.toInstant())
                    .isPhysical(record[BOOK.IS_PHYSICAL] == 1.toByte())
                    .build()
            }
    }

    private fun userProgressFilter(userId: Long?) =
        if (userId != null) ubp.USER_ID.eq(userId) else org.jooq.impl.DSL.falseCondition()

    /** Mirrors AppBookMapper.mapReadProgress: first non-null percent, koreader→kobo→epub→pdf→cbx. */
    private fun readProgress(
        koreader: Double?, kobo: Double?, epub: Double?, pdf: Double?, cbx: Double?
    ): Float? = (koreader ?: kobo ?: epub ?: pdf ?: cbx)?.toFloat()

    /** Mirrors BookEntity.getPrimaryBookFile + AppBookMapper.mapPrimaryFileType. */
    private fun primaryFileType(formatPriorityJson: String?, files: List<FileCandidate>): String? {
        if (files.isEmpty()) return null
        for (format in parseFormatPriority(formatPriorityJson)) {
            val match = files.firstOrNull { it.isBook && it.bookType == format }
            if (match != null) return match.bookType
        }
        return files.first().bookType
    }

    private fun parseFormatPriority(json: String?): List<String> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            objectMapper.readValue(json, object : TypeReference<List<String>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun LocalDateTime.toInstant(): Instant = this.toInstant(ZoneOffset.UTC)
}
