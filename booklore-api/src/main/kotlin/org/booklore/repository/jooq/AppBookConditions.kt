package org.booklore.repository.jooq

import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookShelfMapping.BOOK_SHELF_MAPPING
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.jooq.Condition
import org.jooq.impl.DSL.*
import java.time.LocalDateTime

object AppBookConditions {

    private val bf = BOOK_FILE
    private val bm = BOOK_METADATA
    private val bam = BOOK_METADATA_AUTHOR_MAPPING
    private val bsm = BOOK_SHELF_MAPPING
    private val ubp = USER_BOOK_PROGRESS

    @JvmStatic fun notDeleted(): Condition = BookConditions.notDeleted()

    @JvmStatic fun inLibraries(libraryIds: Collection<Long>?): Condition =
        if (libraryIds.isNullOrEmpty()) noCondition()
        else BOOK.LIBRARY_ID.`in`(libraryIds)

    @JvmStatic fun inLibrary(libraryId: Long?): Condition =
        if (libraryId == null) noCondition()
        else BOOK.LIBRARY_ID.eq(libraryId)

    @JvmStatic fun inShelf(shelfId: Long?): Condition =
        if (shelfId == null) noCondition()
        else BOOK.ID.`in`(
            select(bsm.BOOK_ID).from(bsm).where(bsm.SHELF_ID.eq(shelfId))
        )

    @JvmStatic fun hasDigitalFile(): Condition =
        exists(selectOne().from(bf).where(bf.BOOK_ID.eq(BOOK.ID)))

    @JvmStatic fun hasAudiobookFile(): Condition =
        exists(
            selectOne().from(bf)
                .where(bf.BOOK_ID.eq(BOOK.ID))
                .and(bf.BOOK_TYPE.eq("AUDIOBOOK"))
        )

    @JvmStatic fun hasNonAudiobookFile(): Condition =
        exists(
            selectOne().from(bf)
                .where(bf.BOOK_ID.eq(BOOK.ID))
                .and(bf.BOOK_TYPE.ne("AUDIOBOOK"))
        )

    @JvmStatic fun withFileType(fileType: String?): Condition =
        if (fileType == null) noCondition()
        else exists(
            selectOne().from(bf)
                .where(bf.BOOK_ID.eq(BOOK.ID))
                .and(bf.BOOK_TYPE.eq(fileType))
        )

    @JvmStatic fun hasScannedOn(): Condition = BOOK.SCANNED_ON.isNotNull

    @JvmStatic fun addedWithinDays(days: Int): Condition =
        BOOK.ADDED_ON.ge(LocalDateTime.now().minusDays(days.toLong()))

    @JvmStatic fun searchText(query: String?): Condition {
        if (query.isNullOrBlank()) return noCondition()
        val pattern = "%${query.lowercase().trim()}%"

        val metadataMatch = BOOK.ID.`in`(
            select(bm.BOOK_ID).from(bm)
                .where(
                    lower(bm.TITLE).like(pattern)
                        .or(lower(bm.SERIES_NAME).like(pattern))
                )
        )

        val authorMatch = BOOK.ID.`in`(
            select(bam.BOOK_ID).from(bam)
                .join(AUTHOR).on(AUTHOR.ID.eq(bam.AUTHOR_ID))
                .where(lower(AUTHOR.NAME).like(pattern))
        )

        return metadataMatch.or(authorMatch)
    }

    @JvmStatic fun withReadStatus(status: String?, userId: Long?): Condition {
        if (status == null || userId == null) return noCondition()
        return BOOK.ID.`in`(
            select(ubp.BOOK_ID).from(ubp)
                .where(ubp.USER_ID.eq(userId))
                .and(ubp.READ_STATUS.eq(status))
        )
    }

    @JvmStatic fun inProgress(userId: Long?): Condition {
        if (userId == null) return noCondition()
        return BOOK.ID.`in`(
            select(ubp.BOOK_ID).from(ubp)
                .where(ubp.USER_ID.eq(userId))
                .and(ubp.READ_STATUS.`in`("READING", "RE_READING"))
        )
    }

    @JvmStatic fun withMinRating(minRating: Int?, userId: Long?): Condition {
        if (minRating == null || userId == null) return noCondition()
        return BOOK.ID.`in`(
            select(ubp.BOOK_ID).from(ubp)
                .where(ubp.USER_ID.eq(userId))
                .and(ubp.PERSONAL_RATING.ge(minRating.toByte()))
        )
    }

    @JvmStatic fun withMaxRating(maxRating: Int?, userId: Long?): Condition {
        if (maxRating == null || userId == null) return noCondition()

        if (maxRating == 0) {
            // Unrated: books that have no progress entry with a non-null personalRating
            return BOOK.ID.notIn(
                select(ubp.BOOK_ID).from(ubp)
                    .where(ubp.USER_ID.eq(userId))
                    .and(ubp.PERSONAL_RATING.isNotNull)
            )
        }

        return BOOK.ID.`in`(
            select(ubp.BOOK_ID).from(ubp)
                .where(ubp.USER_ID.eq(userId))
                .and(ubp.PERSONAL_RATING.le(maxRating.toByte()))
        )
    }

    @JvmStatic fun withAuthor(authorName: String?): Condition {
        if (authorName.isNullOrBlank()) return noCondition()
        return BOOK.ID.`in`(
            select(bam.BOOK_ID).from(bam)
                .join(AUTHOR).on(AUTHOR.ID.eq(bam.AUTHOR_ID))
                .where(lower(AUTHOR.NAME).eq(authorName.lowercase().trim()))
        )
    }

    @JvmStatic fun withLanguage(language: String?): Condition {
        if (language.isNullOrBlank()) return noCondition()
        return BOOK.ID.`in`(
            select(bm.BOOK_ID).from(bm)
                .where(lower(bm.LANGUAGE).eq(language.lowercase().trim()))
        )
    }

    @JvmStatic fun inSeries(seriesName: String?): Condition {
        if (seriesName.isNullOrBlank()) return noCondition()
        return BOOK.ID.`in`(
            select(bm.BOOK_ID).from(bm)
                .where(bm.SERIES_NAME.eq(seriesName))
        )
    }
}
