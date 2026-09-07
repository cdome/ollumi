package org.booklore.repository.jooq

import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.records.BookMetadataRecord
import org.jooq.DSLContext
import org.jooq.TableField
import org.springframework.stereotype.Repository

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
