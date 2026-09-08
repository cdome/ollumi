package org.booklore.repository.jooq

import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.model.enums.BookFileType
import org.booklore.repository.jooq.dto.BookCoverUpdate
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class JooqBookRepository(private val dsl: DSLContext) {

    private val bm = BOOK_METADATA
    private val bf = BOOK_FILE

    // ========================================================================
    // Book IDs
    // ========================================================================

    fun findBookIdsByLibraryId(libraryId: Long): Set<Long> =
        dsl.select(BOOK.ID)
            .from(BOOK)
            .where(BookConditions.notDeleted())
            .and(BOOK.LIBRARY_ID.eq(libraryId))
            .fetchSet(BOOK.ID)

    fun findBookIdsByLibraryPathIds(libraryPathIds: Collection<Long>): List<Long> =
        dsl.select(BOOK.ID)
            .from(BOOK)
            .where(BookConditions.notDeleted())
            .and(BOOK.LIBRARY_PATH_ID.`in`(libraryPathIds))
            .fetch(BOOK.ID)

    // ========================================================================
    // Counts
    // ========================================================================

    fun countByIds(bookIds: Collection<Long>): Long =
        dsl.fetchCount(BOOK, BookConditions.notDeleted().and(BOOK.ID.`in`(bookIds))).toLong()

    fun countByLibraryId(libraryId: Long): Long =
        dsl.fetchCount(BOOK, BookConditions.notDeleted().and(BOOK.LIBRARY_ID.eq(libraryId))).toLong()

    fun countByBookType(type: BookFileType): Long =
        countByBookType(type, null)

    fun countByLibraryIdAndBookType(libraryId: Long, type: BookFileType): Long =
        countByBookType(type, libraryId)

    fun countSoftDeleted(): Long =
        dsl.fetchCount(BOOK, BOOK.DELETED.eq(1)).toLong()

    private fun countByBookType(type: BookFileType, libraryId: Long?): Long =
        dsl.select(countDistinct(BOOK.ID))
            .from(BOOK)
            .join(bf).on(bf.BOOK_ID.eq(BOOK.ID))
            .where(BookConditions.notDeleted())
            .and(bf.IS_BOOK.eq(1))
            .and(bf.BOOK_TYPE.eq(type.name))
            .and(if (libraryId != null) BOOK.LIBRARY_ID.eq(libraryId) else noCondition())
            .fetchOne(0, Long::class.java)!!

    // ========================================================================
    // Cover update info
    // ========================================================================

    fun findCoverUpdateInfoByIds(bookIds: Collection<Long>): List<BookCoverUpdate> =
        dsl.select(BOOK.ID, bm.COVER_UPDATED_ON)
            .from(BOOK)
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(BOOK.ID.`in`(bookIds))
            .fetch { record ->
                BookCoverUpdate(
                    id = record[BOOK.ID]!!,
                    coverUpdatedOn = record[bm.COVER_UPDATED_ON]?.toInstant(ZoneOffset.UTC)
                )
            }

    // ========================================================================
    // Distinct series names (Komga API)
    // ========================================================================

    /**
     * Distinct series names; books without a series name are grouped under [unknownSeriesName].
     */
    fun findDistinctSeriesNamesGrouped(unknownSeriesName: String, libraryId: Long?): List<String> {
        val seriesName = coalesce(bm.SERIES_NAME, inline(unknownSeriesName))
        return dsl.selectDistinct(seriesName)
            .from(BOOK)
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(BookConditions.notDeleted())
            .and(if (libraryId != null) BOOK.LIBRARY_ID.eq(libraryId) else noCondition())
            .orderBy(seriesName)
            .fetch(seriesName)
    }

    /**
     * Distinct series names; each book without a series name gets its own entry
     * (metadata title, or the first book file's name as last resort).
     */
    fun findDistinctSeriesNamesUngrouped(libraryId: Long?): List<String> {
        val seriesName = coalesce(bm.SERIES_NAME, bm.TITLE, firstBookFileName())
        return dsl.selectDistinct(seriesName)
            .from(BOOK)
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(BookConditions.notDeleted())
            .and(if (libraryId != null) BOOK.LIBRARY_ID.eq(libraryId) else noCondition())
            .orderBy(seriesName)
            .fetch(seriesName)
    }

    // ========================================================================
    // Books by series name (Komga API) — ID queries for the two-query pattern
    // ========================================================================

    /**
     * IDs of a library's non-deleted books in [seriesName], ordered by series number.
     * Books without a series name match via their first book file's name.
     */
    fun findBookIdsBySeriesNameGrouped(seriesName: String, libraryId: Long): List<Long> =
        findBookIdsBySeries(
            bm.SERIES_NAME.eq(seriesName)
                .or(bm.SERIES_NAME.isNull.and(firstBookFileName().eq(seriesName))),
            libraryId
        )

    /**
     * IDs of a library's non-deleted books in [seriesName], ordered by series number.
     * Books without a series name match via their title, then their first book file's name.
     */
    fun findBookIdsBySeriesNameUngrouped(seriesName: String, libraryId: Long): List<Long> =
        findBookIdsBySeries(
            bm.SERIES_NAME.eq(seriesName)
                .or(bm.SERIES_NAME.isNull.and(bm.TITLE.eq(seriesName)))
                .or(bm.SERIES_NAME.isNull.and(bm.TITLE.isNull).and(firstBookFileName().eq(seriesName))),
            libraryId
        )

    private fun findBookIdsBySeries(seriesCondition: Condition, libraryId: Long): List<Long> =
        dsl.select(BOOK.ID)
            .from(BOOK)
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .where(BookConditions.notDeleted())
            .and(BOOK.LIBRARY_ID.eq(libraryId))
            .and(seriesCondition)
            .orderBy(coalesce(bm.SERIES_NUMBER, inline(0.0)))
            .fetch(BOOK.ID)

    // ========================================================================
    // Writes: soft-delete cleanup and library moves
    // ========================================================================

    fun deleteAllSoftDeleted(): Int =
        dsl.deleteFrom(BOOK)
            .where(BOOK.DELETED.eq(1))
            .execute()

    fun deleteSoftDeletedBefore(cutoff: Instant): Int =
        dsl.deleteFrom(BOOK)
            .where(BOOK.DELETED.eq(1))
            .and(BOOK.DELETED_AT.lt(LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC)))
            .execute()

    fun updateLibrary(bookId: Long, libraryId: Long, libraryPathId: Long) {
        dsl.update(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .set(BOOK.LIBRARY_PATH_ID, libraryPathId)
            .where(BOOK.ID.eq(bookId))
            .execute()
    }

    fun markDeleted(bookIds: Collection<Long>, deletedAt: Instant) {
        if (bookIds.isEmpty()) return
        dsl.update(BOOK)
            .set(BOOK.DELETED, 1.toByte())
            .set(BOOK.DELETED_AT, LocalDateTime.ofInstant(deletedAt, ZoneOffset.UTC))
            .where(BOOK.ID.`in`(bookIds))
            .execute()
    }

    fun markUndeleted(bookIds: Collection<Long>) {
        if (bookIds.isEmpty()) return
        dsl.update(BOOK)
            .set(BOOK.DELETED, 0.toByte())
            .setNull(BOOK.DELETED_AT)
            .where(BOOK.ID.`in`(bookIds))
            .execute()
    }

    fun deleteByIds(bookIds: Collection<Long>): Int {
        if (bookIds.isEmpty()) return 0
        return dsl.deleteFrom(BOOK).where(BOOK.ID.`in`(bookIds)).execute()
    }

    /**
     * Inserts a new book row (e.g. detaching a file into its own book) and returns the generated id.
     * Only columns BookEntity.builder() would set for a bare new book are written; everything else
     * (metadata_match_score, cover hashes, timestamps, similar_books_json) takes its DB-level NULL
     * default, matching what an unenhanced Hibernate INSERT would also leave untouched.
     */
    fun insertBook(libraryId: Long, libraryPathId: Long, addedOn: Instant): Long =
        dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .set(BOOK.LIBRARY_PATH_ID, libraryPathId)
            .set(BOOK.ADDED_ON, LocalDateTime.ofInstant(addedOn, ZoneOffset.UTC))
            .set(BOOK.IS_PHYSICAL, 0.toByte())
            .set(BOOK.DELETED, 0.toByte())
            .returning(BOOK.ID)
            .fetchOne()!!.id!!

    // ========================================================================
    // Writes: book_file CRUD (no cascade — book_file's own FK children,
    // e.g. user_book_file_progress, are ON DELETE CASCADE at the DB level)
    // ========================================================================

    fun deleteBookFileById(bookFileId: Long) {
        dsl.deleteFrom(bf).where(bf.ID.eq(bookFileId)).execute()
    }

    /** Reassigns a file to a different book, also updating its path/name (e.g. after a physical file move). */
    fun reassignFileWithNewNameAndPath(bookFileId: Long, targetBookId: Long, newFileSubPath: String, newFileName: String) {
        dsl.update(bf)
            .set(bf.BOOK_ID, targetBookId)
            .set(bf.FILE_SUB_PATH, newFileSubPath)
            .set(bf.FILE_NAME, newFileName)
            .where(bf.ID.eq(bookFileId))
            .execute()
    }

    fun insertBookFile(
        bookId: Long,
        fileName: String,
        fileSubPath: String,
        isBookFormat: Boolean,
        folderBased: Boolean,
        bookType: BookFileType?,
        fileSizeKb: Long?,
        initialHash: String?,
        currentHash: String?,
        description: String?,
        addedOn: Instant
    ): Long =
        dsl.insertInto(bf)
            .set(bf.BOOK_ID, bookId)
            .set(bf.FILE_NAME, fileName)
            .set(bf.FILE_SUB_PATH, fileSubPath)
            .set(bf.IS_BOOK, if (isBookFormat) 1.toByte() else 0.toByte())
            .set(bf.IS_FOLDER_BASED, if (folderBased) 1.toByte() else 0.toByte())
            .set(bf.BOOK_TYPE, bookType?.name)
            .set(bf.FILE_SIZE_KB, fileSizeKb)
            .set(bf.INITIAL_HASH, initialHash)
            .set(bf.CURRENT_HASH, currentHash)
            .set(bf.DESCRIPTION, description)
            .set(bf.ADDED_ON, LocalDateTime.ofInstant(addedOn, ZoneOffset.UTC))
            .returning(bf.ID)
            .fetchOne()!!.id!!

    // ========================================================================
    // Writes: cover updates (book + book_metadata timestamp/hash columns only —
    // no cascade, these rows already exist)
    // ========================================================================

    fun updateCoverMetadata(bookId: Long, bookCoverHash: String, now: Instant) {
        val ts = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
        dsl.update(BOOK)
            .set(BOOK.BOOK_COVER_HASH, bookCoverHash)
            .set(BOOK.METADATA_UPDATED_AT, ts)
            .where(BOOK.ID.eq(bookId))
            .execute()
        dsl.update(bm)
            .set(bm.COVER_UPDATED_ON, ts)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }

    fun updateAudiobookCoverMetadata(bookId: Long, audiobookCoverHash: String, now: Instant) {
        val ts = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
        dsl.update(BOOK)
            .set(BOOK.AUDIOBOOK_COVER_HASH, audiobookCoverHash)
            .set(BOOK.METADATA_UPDATED_AT, ts)
            .where(BOOK.ID.eq(bookId))
            .execute()
        dsl.update(bm)
            .set(bm.AUDIOBOOK_COVER_UPDATED_ON, ts)
            .where(bm.BOOK_ID.eq(bookId))
            .execute()
    }

    fun updateBookFileHash(bookFileId: Long, hash: String) {
        dsl.update(bf)
            .set(bf.CURRENT_HASH, hash)
            .where(bf.ID.eq(bookFileId))
            .execute()
    }

    fun updateBookCoverHash(bookId: Long, hash: String) {
        dsl.update(BOOK)
            .set(BOOK.BOOK_COVER_HASH, hash)
            .where(BOOK.ID.eq(bookId))
            .execute()
    }

    fun updateMetadataUpdatedAt(bookId: Long, now: Instant) {
        dsl.update(BOOK)
            .set(BOOK.METADATA_UPDATED_AT, LocalDateTime.ofInstant(now, ZoneOffset.UTC))
            .where(BOOK.ID.eq(bookId))
            .execute()
    }

    fun updateMetadataForWriteUpdatedAt(bookId: Long, now: Instant) {
        dsl.update(BOOK)
            .set(BOOK.METADATA_FOR_WRITE_UPDATED_AT, LocalDateTime.ofInstant(now, ZoneOffset.UTC))
            .where(BOOK.ID.eq(bookId))
            .execute()
    }

    fun updateMetadataMatchScore(bookId: Long, score: Float?) {
        dsl.update(BOOK)
            .set(BOOK.METADATA_MATCH_SCORE, score?.toDouble())
            .where(BOOK.ID.eq(bookId))
            .execute()
    }

    private fun firstBookFileName(): Field<String> {
        val bf2 = BOOK_FILE.`as`("bf2")
        return field(
            select(bf2.FILE_NAME)
                .from(bf2)
                .where(bf2.BOOK_ID.eq(BOOK.ID))
                .and(bf2.IS_BOOK.eq(1))
                .orderBy(bf2.ID.asc())
                .limit(1)
        )
    }
}
