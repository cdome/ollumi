package org.booklore.repository.jooq

import org.booklore.app.dto.AppBookDetail
import org.booklore.app.dto.AppBookFile
import org.booklore.app.dto.AppShelfSummary
import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookMetadataCategoryMapping.BOOK_METADATA_CATEGORY_MAPPING
import org.booklore.jooq.tables.BookShelfMapping.BOOK_SHELF_MAPPING
import org.booklore.jooq.tables.Category.CATEGORY
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Shelf.SHELF
import org.booklore.jooq.tables.UserBookFileProgress.USER_BOOK_FILE_PROGRESS
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.selectCount
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * jOOQ read model for [AppBookDetail]: builds the full single-book detail from
 * batched jOOQ queries instead of a JPA @EntityGraph load plus lazy access to
 * authors/categories/shelves. Derivations (primary file, read progress, per-format
 * progress objects) mirror BookEntity / AppBookMapper.
 */
@Repository
class JooqAppBookDetailRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) {

    private val bm = BOOK_METADATA
    private val ubp = USER_BOOK_PROGRESS
    private val bf = BOOK_FILE

    private data class FileRow(
        val id: Long,
        val fileName: String?,
        val isBook: Boolean,
        val folderBased: Boolean,
        val bookType: String?,
        val archiveType: String?,
        val fileSizeKb: Long?,
        val addedOn: LocalDateTime?
    )

    fun findDetailById(bookId: Long, userId: Long?): AppBookDetail? {
        val record = dsl.select(
            BOOK.ID, BOOK.ADDED_ON, BOOK.IS_PHYSICAL, BOOK.LIBRARY_ID,
            bm.TITLE, bm.SUBTITLE, bm.DESCRIPTION, bm.PUBLISHER, bm.PUBLISHED_DATE, bm.PAGE_COUNT,
            bm.ISBN_13, bm.LANGUAGE, bm.GOODREADS_RATING, bm.GOODREADS_REVIEW_COUNT,
            bm.SERIES_NAME, bm.SERIES_NUMBER, bm.COVER_UPDATED_ON, bm.AUDIOBOOK_COVER_UPDATED_ON,
            LIBRARY.NAME, LIBRARY.FORMAT_PRIORITY,
            ubp.READ_STATUS, ubp.PERSONAL_RATING, ubp.LAST_READ_TIME,
            ubp.KOREADER_PROGRESS_PERCENT, ubp.KOBO_PROGRESS_PERCENT, ubp.EPUB_PROGRESS_PERCENT,
            ubp.PDF_PROGRESS_PERCENT, ubp.CBX_PROGRESS_PERCENT,
            ubp.EPUB_PROGRESS, ubp.EPUB_PROGRESS_HREF, ubp.PDF_PROGRESS, ubp.CBX_PROGRESS,
            ubp.KOREADER_DEVICE, ubp.KOREADER_DEVICE_ID, ubp.KOREADER_LAST_SYNC_TIME
        )
            .from(BOOK)
            .leftJoin(bm).on(bm.BOOK_ID.eq(BOOK.ID))
            .leftJoin(LIBRARY).on(LIBRARY.ID.eq(BOOK.LIBRARY_ID))
            .leftJoin(ubp).on(ubp.BOOK_ID.eq(BOOK.ID).and(userProgressFilter(userId)))
            .where(BOOK.ID.eq(bookId))
            .and(BookConditions.notDeleted())
            .fetchOne() ?: return null

        val files = fetchFiles(bookId)
        val primaryId = primaryFileId(record[LIBRARY.FORMAT_PRIORITY], files)

        return AppBookDetail.builder()
            .id(bookId)
            .title(record[bm.TITLE])
            .authors(fetchAuthors(bookId))
            .thumbnailUrl("/api/books/$bookId/cover")
            .readStatus(record[ubp.READ_STATUS])
            .personalRating(record[ubp.PERSONAL_RATING]?.toInt())
            .seriesName(record[bm.SERIES_NAME])
            .seriesNumber(record[bm.SERIES_NUMBER]?.toFloat())
            .libraryId(record[BOOK.LIBRARY_ID])
            .addedOn(record[BOOK.ADDED_ON]?.toInstant())
            .lastReadTime(record[ubp.LAST_READ_TIME]?.toInstant())
            .subtitle(record[bm.SUBTITLE])
            .description(record[bm.DESCRIPTION])
            .categories(fetchCategories(bookId))
            .publisher(record[bm.PUBLISHER])
            .publishedDate(record[bm.PUBLISHED_DATE])
            .pageCount(record[bm.PAGE_COUNT])
            .isbn13(record[bm.ISBN_13])
            .language(record[bm.LANGUAGE])
            .goodreadsRating(record[bm.GOODREADS_RATING])
            .goodreadsReviewCount(record[bm.GOODREADS_REVIEW_COUNT])
            .libraryName(record[LIBRARY.NAME])
            .shelves(fetchShelves(bookId))
            .readProgress(
                readProgress(
                    record[ubp.KOREADER_PROGRESS_PERCENT], record[ubp.KOBO_PROGRESS_PERCENT],
                    record[ubp.EPUB_PROGRESS_PERCENT], record[ubp.PDF_PROGRESS_PERCENT],
                    record[ubp.CBX_PROGRESS_PERCENT]
                )
            )
            .primaryFileType(files.firstOrNull { it.id == primaryId }?.bookType)
            .fileTypes(files.mapNotNull { it.bookType }.distinct())
            .files(mapFiles(bookId, files, primaryId))
            .coverUpdatedOn(record[bm.COVER_UPDATED_ON]?.toInstant())
            .audiobookCoverUpdatedOn(record[bm.AUDIOBOOK_COVER_UPDATED_ON]?.toInstant())
            .isPhysical(record[BOOK.IS_PHYSICAL] == 1.toByte())
            .epubProgress(epubProgress(record))
            .pdfProgress(pdfProgress(record))
            .cbxProgress(cbxProgress(record))
            .koreaderProgress(koreaderProgress(record))
            .audiobookProgress(fetchAudiobookProgress(bookId, userId))
            .build()
    }

    // ------------------------------------------------------------------------
    // Batched multi-valued fetches
    // ------------------------------------------------------------------------

    private fun fetchAuthors(bookId: Long): List<String> =
        dsl.select(AUTHOR.NAME)
            .from(BOOK_METADATA_AUTHOR_MAPPING)
            .join(AUTHOR).on(AUTHOR.ID.eq(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID))
            .where(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID.eq(bookId))
            .orderBy(BOOK_METADATA_AUTHOR_MAPPING.SORT_ORDER)
            .fetch(AUTHOR.NAME)

    private fun fetchCategories(bookId: Long): Set<String> =
        dsl.select(CATEGORY.NAME)
            .from(BOOK_METADATA_CATEGORY_MAPPING)
            .join(CATEGORY).on(CATEGORY.ID.eq(BOOK_METADATA_CATEGORY_MAPPING.CATEGORY_ID))
            .where(BOOK_METADATA_CATEGORY_MAPPING.BOOK_ID.eq(bookId))
            .fetchSet(CATEGORY.NAME)

    private fun fetchShelves(bookId: Long): List<AppShelfSummary> {
        val bookCount = field(
            selectCount().from(BOOK_SHELF_MAPPING).where(BOOK_SHELF_MAPPING.SHELF_ID.eq(SHELF.ID))
        )
        return dsl.select(SHELF.ID, SHELF.NAME, SHELF.ICON, SHELF.IS_PUBLIC, bookCount)
            .from(BOOK_SHELF_MAPPING)
            .join(SHELF).on(SHELF.ID.eq(BOOK_SHELF_MAPPING.SHELF_ID))
            .where(BOOK_SHELF_MAPPING.BOOK_ID.eq(bookId))
            .fetch { r ->
                AppShelfSummary.builder()
                    .id(r[SHELF.ID])
                    .name(r[SHELF.NAME])
                    .icon(r[SHELF.ICON])
                    .bookCount(r[bookCount])
                    .publicShelf(r[SHELF.IS_PUBLIC] == 1.toByte())
                    .build()
            }
    }

    private fun fetchFiles(bookId: Long): List<FileRow> =
        dsl.select(
            bf.ID, bf.FILE_NAME, bf.IS_BOOK, bf.IS_FOLDER_BASED, bf.BOOK_TYPE,
            bf.ARCHIVE_TYPE, bf.FILE_SIZE_KB, bf.ADDED_ON
        )
            .from(bf)
            .where(bf.BOOK_ID.eq(bookId))
            .orderBy(bf.ID)
            .fetch { r ->
                FileRow(
                    id = r[bf.ID]!!,
                    fileName = r[bf.FILE_NAME],
                    isBook = r[bf.IS_BOOK] == 1.toByte(),
                    folderBased = r[bf.IS_FOLDER_BASED] == 1.toByte(),
                    bookType = r[bf.BOOK_TYPE],
                    archiveType = r[bf.ARCHIVE_TYPE],
                    fileSizeKb = r[bf.FILE_SIZE_KB],
                    addedOn = r[bf.ADDED_ON]
                )
            }

    private fun mapFiles(bookId: Long, files: List<FileRow>, primaryId: Long?): List<AppBookFile> =
        files.filter { it.isBook && it.bookType != null }
            .map { f ->
                AppBookFile.builder()
                    .id(f.id)
                    .bookId(bookId)
                    .fileName(f.fileName)
                    .isBook(f.isBook)
                    .folderBased(f.folderBased)
                    .bookType(f.bookType)
                    .archiveType(f.archiveType)
                    .fileSizeKb(f.fileSizeKb)
                    .extension(extensionOf(f.fileName))
                    .addedOn(f.addedOn?.toInstant())
                    .isPrimary(f.id == primaryId)
                    .build()
            }

    private fun fetchAudiobookProgress(bookId: Long, userId: Long?): AppBookDetail.AudiobookProgress? {
        val ubfp = USER_BOOK_FILE_PROGRESS
        val record = dsl.select(
            ubfp.POSITION_DATA, ubfp.POSITION_HREF, ubfp.PROGRESS_PERCENT, ubfp.LAST_READ_TIME
        )
            .from(ubfp)
            .join(bf).on(bf.ID.eq(ubfp.BOOK_FILE_ID))
            .where(bf.BOOK_ID.eq(bookId))
            .and(bf.BOOK_TYPE.eq("AUDIOBOOK"))
            .and(if (userId != null) ubfp.USER_ID.eq(userId) else org.jooq.impl.DSL.falseCondition())
            .orderBy(ubfp.LAST_READ_TIME.desc())
            .limit(1)
            .fetchOne() ?: return null

        return AppBookDetail.AudiobookProgress.builder()
            .positionMs(record[ubfp.POSITION_DATA]?.toLongOrNull())
            .trackIndex(record[ubfp.POSITION_HREF]?.toIntOrNull())
            .percentage(record[ubfp.PROGRESS_PERCENT]?.toFloat())
            .updatedAt(record[ubfp.LAST_READ_TIME]?.toInstant())
            .build()
    }

    // ------------------------------------------------------------------------
    // Derivations mirroring AppBookMapper
    // ------------------------------------------------------------------------

    private fun epubProgress(r: Record): AppBookDetail.EpubProgress? {
        val cfi = r[ubp.EPUB_PROGRESS] ?: return null
        return AppBookDetail.EpubProgress.builder()
            .cfi(cfi)
            .href(r[ubp.EPUB_PROGRESS_HREF])
            .percentage(r[ubp.EPUB_PROGRESS_PERCENT]?.toFloat())
            .updatedAt(r[ubp.LAST_READ_TIME]?.toInstant())
            .build()
    }

    private fun pdfProgress(r: Record): AppBookDetail.PdfProgress? {
        val page = r[ubp.PDF_PROGRESS] ?: return null
        return AppBookDetail.PdfProgress.builder()
            .page(page)
            .percentage(r[ubp.PDF_PROGRESS_PERCENT]?.toFloat())
            .updatedAt(r[ubp.LAST_READ_TIME]?.toInstant())
            .build()
    }

    private fun cbxProgress(r: Record): AppBookDetail.CbxProgress? {
        val page = r[ubp.CBX_PROGRESS] ?: return null
        return AppBookDetail.CbxProgress.builder()
            .page(page)
            .percentage(r[ubp.CBX_PROGRESS_PERCENT]?.toFloat())
            .updatedAt(r[ubp.LAST_READ_TIME]?.toInstant())
            .build()
    }

    private fun koreaderProgress(r: Record): AppBookDetail.KoreaderProgress? {
        val percent = r[ubp.KOREADER_PROGRESS_PERCENT] ?: return null
        return AppBookDetail.KoreaderProgress.builder()
            .percentage(percent.toFloat())
            .device(r[ubp.KOREADER_DEVICE])
            .deviceId(r[ubp.KOREADER_DEVICE_ID])
            .lastSyncTime(r[ubp.KOREADER_LAST_SYNC_TIME]?.toInstant())
            .build()
    }

    private fun readProgress(
        koreader: Double?, kobo: Double?, epub: Double?, pdf: Double?, cbx: Double?
    ): Float? = (koreader ?: kobo ?: epub ?: pdf ?: cbx)?.toFloat()

    private fun primaryFileId(formatPriorityJson: String?, files: List<FileRow>): Long? {
        if (files.isEmpty()) return null
        for (format in parseFormatPriority(formatPriorityJson)) {
            val match = files.firstOrNull { it.isBook && it.bookType == format }
            if (match != null) return match.id
        }
        return files.first().id
    }

    private fun parseFormatPriority(json: String?): List<String> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            objectMapper.readValue(json, object : TypeReference<List<String>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extensionOf(fileName: String?): String? {
        if (fileName == null) return null
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(dot + 1) else null
    }

    private fun userProgressFilter(userId: Long?) =
        if (userId != null) ubp.USER_ID.eq(userId) else org.jooq.impl.DSL.falseCondition()

    private fun LocalDateTime.toInstant(): Instant = this.toInstant(ZoneOffset.UTC)
}
