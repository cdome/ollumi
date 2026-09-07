package org.booklore.repository.jooq

import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.records.BookMetadataRecord
import org.booklore.model.entity.BookMetadataEntity
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.TableField
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * jOOQ write access to book_metadata's own scalar columns (not the @ManyToMany relations, see
 * JooqBookMetadataRelationsRepository). Used by bulk consolidate/delete operations that previously
 * relied on BookMetadataRepository.saveAll() + the @PreUpdate updateSearchText() hook.
 */
@Repository
class JooqBookMetadataRepository(private val dsl: DSLContext) {

    private val bm = BOOK_METADATA

    companion object {
        /**
         * Maps BookMetadataEntity's Boolean lock-field names to their jOOQ columns. Four legacy DB
         * columns (all_fields_locked, thumbnail_locked, rating_locked, review_count_locked) have no
         * entity field and are intentionally excluded - pre-existing dead columns, not touched here.
         */
        private val LOCK_FIELDS: Map<String, TableField<BookMetadataRecord, Byte>> = mapOf(
            "titleLocked" to BOOK_METADATA.TITLE_LOCKED,
            "subtitleLocked" to BOOK_METADATA.SUBTITLE_LOCKED,
            "publisherLocked" to BOOK_METADATA.PUBLISHER_LOCKED,
            "publishedDateLocked" to BOOK_METADATA.PUBLISHED_DATE_LOCKED,
            "descriptionLocked" to BOOK_METADATA.DESCRIPTION_LOCKED,
            "isbn13Locked" to BOOK_METADATA.ISBN_13_LOCKED,
            "isbn10Locked" to BOOK_METADATA.ISBN_10_LOCKED,
            "asinLocked" to BOOK_METADATA.ASIN_LOCKED,
            "pageCountLocked" to BOOK_METADATA.PAGE_COUNT_LOCKED,
            "languageLocked" to BOOK_METADATA.LANGUAGE_LOCKED,
            "amazonRatingLocked" to BOOK_METADATA.AMAZON_RATING_LOCKED,
            "amazonReviewCountLocked" to BOOK_METADATA.AMAZON_REVIEW_COUNT_LOCKED,
            "goodreadsRatingLocked" to BOOK_METADATA.GOODREADS_RATING_LOCKED,
            "goodreadsReviewCountLocked" to BOOK_METADATA.GOODREADS_REVIEW_COUNT_LOCKED,
            "hardcoverRatingLocked" to BOOK_METADATA.HARDCOVER_RATING_LOCKED,
            "hardcoverReviewCountLocked" to BOOK_METADATA.HARDCOVER_REVIEW_COUNT_LOCKED,
            "coverLocked" to BOOK_METADATA.COVER_LOCKED,
            "audiobookCoverLocked" to BOOK_METADATA.AUDIOBOOK_COVER_LOCKED,
            "seriesNameLocked" to BOOK_METADATA.SERIES_NAME_LOCKED,
            "seriesNumberLocked" to BOOK_METADATA.SERIES_NUMBER_LOCKED,
            "seriesTotalLocked" to BOOK_METADATA.SERIES_TOTAL_LOCKED,
            "authorsLocked" to BOOK_METADATA.AUTHORS_LOCKED,
            "categoriesLocked" to BOOK_METADATA.CATEGORIES_LOCKED,
            "moodsLocked" to BOOK_METADATA.MOODS_LOCKED,
            "tagsLocked" to BOOK_METADATA.TAGS_LOCKED,
            "goodreadsIdLocked" to BOOK_METADATA.GOODREADS_ID_LOCKED,
            "hardcoverIdLocked" to BOOK_METADATA.HARDCOVER_ID_LOCKED,
            "hardcoverBookIdLocked" to BOOK_METADATA.HARDCOVER_BOOK_ID_LOCKED,
            "googleIdLocked" to BOOK_METADATA.GOOGLE_ID_LOCKED,
            "comicvineIdLocked" to BOOK_METADATA.COMICVINE_ID_LOCKED,
            "lubimyczytacIdLocked" to BOOK_METADATA.LUBIMYCZYTAC_ID_LOCKED,
            "lubimyczytacRatingLocked" to BOOK_METADATA.LUBIMYCZYTAC_RATING_LOCKED,
            "ranobedbIdLocked" to BOOK_METADATA.RANOBEDB_ID_LOCKED,
            "ranobedbRatingLocked" to BOOK_METADATA.RANOBEDB_RATING_LOCKED,
            "audibleIdLocked" to BOOK_METADATA.AUDIBLE_ID_LOCKED,
            "audibleRatingLocked" to BOOK_METADATA.AUDIBLE_RATING_LOCKED,
            "audibleReviewCountLocked" to BOOK_METADATA.AUDIBLE_REVIEW_COUNT_LOCKED,
            "reviewsLocked" to BOOK_METADATA.REVIEWS_LOCKED,
            "narratorLocked" to BOOK_METADATA.NARRATOR_LOCKED,
            "abridgedLocked" to BOOK_METADATA.ABRIDGED_LOCKED,
            "ageRatingLocked" to BOOK_METADATA.AGE_RATING_LOCKED,
            "contentRatingLocked" to BOOK_METADATA.CONTENT_RATING_LOCKED,
        )

        @JvmStatic
        fun isKnownLockField(name: String): Boolean = LOCK_FIELDS.containsKey(name)
    }

    /** name must be a key in LOCK_FIELDS (checked by the caller); throws IllegalArgumentException otherwise. */
    fun updateLockFields(bookIds: Collection<Long>, fieldActions: Map<String, Boolean>) {
        if (bookIds.isEmpty() || fieldActions.isEmpty()) return
        val values: Map<TableField<BookMetadataRecord, Byte>, Byte> = fieldActions.entries.associate { (name, locked) ->
            val field = LOCK_FIELDS[name] ?: throw IllegalArgumentException("Unknown lock field: $name")
            field to toByte(locked)
        }
        dsl.update(bm).set(values).where(bm.BOOK_ID.`in`(bookIds)).execute()
    }

    fun lockAllFields(bookIds: Collection<Long>, lock: Boolean) {
        if (bookIds.isEmpty()) return
        val value = toByte(lock)
        val values = LOCK_FIELDS.values.associateWith { value }
        dsl.update(bm).set(values).where(bm.BOOK_ID.`in`(bookIds)).execute()
    }

    private fun toByte(value: Boolean): Byte = if (value) 1 else 0
    private fun toByteOrNull(value: Boolean?): Byte? = value?.let { if (it) 1 else 0 }

    /**
     * Writes every book_metadata scalar + lock column from the entity's CURRENT in-memory state,
     * replacing BookMetadataEntity's @DynamicUpdate Hibernate flush. Callers must call
     * metadata.updateSearchText() first (trims string fields + recomputes search_text), matching
     * what the old @PreUpdate hook did on every dirty flush. rating/review_count and the embedding_*
     * columns are dead/out-of-scope for this write and intentionally omitted (see LOCK_FIELDS doc
     * for the analogous dead-lock-column note).
     */
    fun updateFullRow(metadata: BookMetadataEntity) {
        val bookId = metadata.bookId ?: return
        val values: Map<Field<*>, Any?> = mapOf(
            bm.TITLE to metadata.title,
            bm.SUBTITLE to metadata.subtitle,
            bm.PUBLISHER to metadata.publisher,
            bm.PUBLISHED_DATE to metadata.publishedDate,
            bm.DESCRIPTION to metadata.description,
            bm.SERIES_NAME to metadata.seriesName,
            bm.SERIES_NUMBER to metadata.seriesNumber?.toDouble(),
            bm.SERIES_TOTAL to metadata.seriesTotal,
            bm.ISBN_13 to metadata.isbn13,
            bm.ISBN_10 to metadata.isbn10,
            bm.PAGE_COUNT to metadata.pageCount,
            bm.LANGUAGE to metadata.language,
            bm.COVER_UPDATED_ON to metadata.coverUpdatedOn?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
            bm.AUDIOBOOK_COVER_UPDATED_ON to metadata.audiobookCoverUpdatedOn?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
            bm.AMAZON_RATING to metadata.amazonRating,
            bm.AMAZON_REVIEW_COUNT to metadata.amazonReviewCount,
            bm.GOODREADS_RATING to metadata.goodreadsRating,
            bm.GOODREADS_REVIEW_COUNT to metadata.goodreadsReviewCount,
            bm.HARDCOVER_RATING to metadata.hardcoverRating,
            bm.HARDCOVER_REVIEW_COUNT to metadata.hardcoverReviewCount,
            bm.ASIN to metadata.asin,
            bm.GOODREADS_ID to metadata.goodreadsId,
            bm.HARDCOVER_ID to metadata.hardcoverId,
            bm.HARDCOVER_BOOK_ID to metadata.hardcoverBookId,
            bm.GOOGLE_ID to metadata.googleId,
            bm.COMICVINE_ID to metadata.comicvineId,
            bm.LUBIMYCZYTAC_ID to metadata.lubimyczytacId,
            bm.LUBIMYCZYTAC_RATING to metadata.lubimyczytacRating,
            bm.RANOBEDB_ID to metadata.ranobedbId,
            bm.RANOBEDB_RATING to metadata.ranobedbRating,
            bm.AUDIBLE_ID to metadata.audibleId,
            bm.AUDIBLE_RATING to metadata.audibleRating,
            bm.AUDIBLE_REVIEW_COUNT to metadata.audibleReviewCount,
            bm.NARRATOR to metadata.narrator,
            bm.ABRIDGED to toByteOrNull(metadata.abridged),
            bm.SEARCH_TEXT to metadata.searchText,
            bm.AGE_RATING to metadata.ageRating,
            bm.CONTENT_RATING to metadata.contentRating,
            bm.TITLE_LOCKED to toByteOrNull(metadata.titleLocked),
            bm.SUBTITLE_LOCKED to toByteOrNull(metadata.subtitleLocked),
            bm.PUBLISHER_LOCKED to toByteOrNull(metadata.publisherLocked),
            bm.PUBLISHED_DATE_LOCKED to toByteOrNull(metadata.publishedDateLocked),
            bm.DESCRIPTION_LOCKED to toByteOrNull(metadata.descriptionLocked),
            bm.ISBN_13_LOCKED to toByteOrNull(metadata.isbn13Locked),
            bm.ISBN_10_LOCKED to toByteOrNull(metadata.isbn10Locked),
            bm.ASIN_LOCKED to toByteOrNull(metadata.asinLocked),
            bm.PAGE_COUNT_LOCKED to toByteOrNull(metadata.pageCountLocked),
            bm.LANGUAGE_LOCKED to toByteOrNull(metadata.languageLocked),
            bm.AMAZON_RATING_LOCKED to toByteOrNull(metadata.amazonRatingLocked),
            bm.AMAZON_REVIEW_COUNT_LOCKED to toByteOrNull(metadata.amazonReviewCountLocked),
            bm.GOODREADS_RATING_LOCKED to toByteOrNull(metadata.goodreadsRatingLocked),
            bm.GOODREADS_REVIEW_COUNT_LOCKED to toByteOrNull(metadata.goodreadsReviewCountLocked),
            bm.HARDCOVER_RATING_LOCKED to toByteOrNull(metadata.hardcoverRatingLocked),
            bm.HARDCOVER_REVIEW_COUNT_LOCKED to toByteOrNull(metadata.hardcoverReviewCountLocked),
            bm.COVER_LOCKED to toByteOrNull(metadata.coverLocked),
            bm.AUDIOBOOK_COVER_LOCKED to toByteOrNull(metadata.audiobookCoverLocked),
            bm.SERIES_NAME_LOCKED to toByteOrNull(metadata.seriesNameLocked),
            bm.SERIES_NUMBER_LOCKED to toByteOrNull(metadata.seriesNumberLocked),
            bm.SERIES_TOTAL_LOCKED to toByteOrNull(metadata.seriesTotalLocked),
            bm.AUTHORS_LOCKED to toByteOrNull(metadata.authorsLocked),
            bm.CATEGORIES_LOCKED to toByteOrNull(metadata.categoriesLocked),
            bm.MOODS_LOCKED to toByteOrNull(metadata.moodsLocked),
            bm.TAGS_LOCKED to toByteOrNull(metadata.tagsLocked),
            bm.GOODREADS_ID_LOCKED to toByteOrNull(metadata.goodreadsIdLocked),
            bm.HARDCOVER_ID_LOCKED to toByteOrNull(metadata.hardcoverIdLocked),
            bm.HARDCOVER_BOOK_ID_LOCKED to toByteOrNull(metadata.hardcoverBookIdLocked),
            bm.GOOGLE_ID_LOCKED to toByteOrNull(metadata.googleIdLocked),
            bm.COMICVINE_ID_LOCKED to toByteOrNull(metadata.comicvineIdLocked),
            bm.LUBIMYCZYTAC_ID_LOCKED to toByteOrNull(metadata.lubimyczytacIdLocked),
            bm.LUBIMYCZYTAC_RATING_LOCKED to toByteOrNull(metadata.lubimyczytacRatingLocked),
            bm.RANOBEDB_ID_LOCKED to toByteOrNull(metadata.ranobedbIdLocked),
            bm.RANOBEDB_RATING_LOCKED to toByteOrNull(metadata.ranobedbRatingLocked),
            bm.AUDIBLE_ID_LOCKED to toByteOrNull(metadata.audibleIdLocked),
            bm.AUDIBLE_RATING_LOCKED to toByteOrNull(metadata.audibleRatingLocked),
            bm.AUDIBLE_REVIEW_COUNT_LOCKED to toByteOrNull(metadata.audibleReviewCountLocked),
            bm.REVIEWS_LOCKED to toByteOrNull(metadata.reviewsLocked),
            bm.NARRATOR_LOCKED to toByteOrNull(metadata.narratorLocked),
            bm.ABRIDGED_LOCKED to toByteOrNull(metadata.abridgedLocked),
            bm.AGE_RATING_LOCKED to toByteOrNull(metadata.ageRatingLocked),
            bm.CONTENT_RATING_LOCKED to toByteOrNull(metadata.contentRatingLocked),
        )
        dsl.update(bm).set(values).where(bm.BOOK_ID.eq(bookId)).execute()
    }

    fun updateSeriesName(bookId: Long, seriesName: String?, searchText: String?) {
        dsl.update(bm)
            .set(bm.SERIES_NAME, seriesName)
            .set(bm.SEARCH_TEXT, searchText)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }

    fun clearSeries(bookId: Long, searchText: String?) {
        dsl.update(bm)
            .setNull(bm.SERIES_NAME)
            .setNull(bm.SERIES_NUMBER)
            .setNull(bm.SERIES_TOTAL)
            .set(bm.SEARCH_TEXT, searchText)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }

    fun updatePublisher(bookId: Long, publisher: String?) {
        dsl.update(bm)
            .set(bm.PUBLISHER, publisher)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }

    fun updateLanguage(bookId: Long, language: String?) {
        dsl.update(bm)
            .set(bm.LANGUAGE, language)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }

    fun updateSearchText(bookId: Long, searchText: String?) {
        dsl.update(bm)
            .set(bm.SEARCH_TEXT, searchText)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }
}
