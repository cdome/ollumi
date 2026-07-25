package org.booklore.repository.jooq

import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookMetadataCategoryMapping.BOOK_METADATA_CATEGORY_MAPPING
import org.booklore.jooq.tables.BookMetadataMoodMapping.BOOK_METADATA_MOOD_MAPPING
import org.booklore.jooq.tables.BookMetadataTagMapping.BOOK_METADATA_TAG_MAPPING
import org.booklore.jooq.tables.BookShelfMapping.BOOK_SHELF_MAPPING
import org.booklore.jooq.tables.Category.CATEGORY
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.LibraryPath.LIBRARY_PATH
import org.booklore.jooq.tables.Mood.MOOD
import org.booklore.jooq.tables.Shelf.SHELF
import org.booklore.jooq.tables.Tag.TAG
import org.booklore.jooq.tables.records.BookMetadataRecord
import org.booklore.model.dto.Book
import org.booklore.model.dto.BookFile
import org.booklore.model.dto.BookMetadata
import org.booklore.model.dto.LibraryPath
import org.booklore.model.dto.Shelf
import org.booklore.model.enums.BookFileType
import org.booklore.model.enums.IconType
import org.booklore.util.ArchiveUtils
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.selectCount
import org.springframework.stereotype.Repository
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * jOOQ read model for the web [Book] DTO, reproducing BookMapperV2.toDTO from
 * batched jOOQ queries instead of a JPA @EntityGraph load. Increment 1 covers
 * Book scalars, the full BookMetadata scalar/lock fields, authors/categories/
 * moods/tags, shelves and files. Comic metadata, audiobook metadata and book
 * reviews are added in later increments.
 *
 * Fidelity is pinned by JooqBookReadRepositoryGoldenMasterTest (recursive
 * comparison against MapStruct). Known divergence: the extension of
 * folder-based files is filesystem-derived and cannot be reproduced here.
 */
@Repository
class JooqBookReadRepository(private val dsl: DSLContext) {

    private data class BookScalars(
        val id: Long,
        val libraryId: Long?,
        val libraryName: String?,
        val libraryPathId: Long?,
        val libraryPathValue: String?,
        val formatPriority: String?,
        val addedOn: LocalDateTime?,
        val metadataMatchScore: Double?,
        val isPhysical: Byte?
    )

    private data class FileRow(
        val id: Long,
        val fileName: String?,
        val fileSubPath: String?,
        val isBook: Boolean,
        val folderBased: Boolean,
        val bookType: String?,
        val archiveType: String?,
        val fileSizeKb: Long?,
        val description: String?,
        val addedOn: LocalDateTime?
    )

    fun findByIds(bookIds: Collection<Long>): List<Book> {
        if (bookIds.isEmpty()) return emptyList()

        val scalars = dsl.select(
            BOOK.ID, BOOK.LIBRARY_ID, LIBRARY.NAME, BOOK.LIBRARY_PATH_ID, LIBRARY_PATH.PATH,
            LIBRARY.FORMAT_PRIORITY, BOOK.ADDED_ON, BOOK.METADATA_MATCH_SCORE, BOOK.IS_PHYSICAL
        )
            .from(BOOK)
            .leftJoin(LIBRARY).on(LIBRARY.ID.eq(BOOK.LIBRARY_ID))
            .leftJoin(LIBRARY_PATH).on(LIBRARY_PATH.ID.eq(BOOK.LIBRARY_PATH_ID))
            .where(BOOK.ID.`in`(bookIds))
            .fetch { r ->
                BookScalars(
                    id = r[BOOK.ID]!!,
                    libraryId = r[BOOK.LIBRARY_ID],
                    libraryName = r[LIBRARY.NAME],
                    libraryPathId = r[BOOK.LIBRARY_PATH_ID],
                    libraryPathValue = r[LIBRARY_PATH.PATH],
                    formatPriority = r[LIBRARY.FORMAT_PRIORITY],
                    addedOn = r[BOOK.ADDED_ON],
                    metadataMatchScore = r[BOOK.METADATA_MATCH_SCORE],
                    isPhysical = r[BOOK.IS_PHYSICAL]
                )
            }

        val metaByBook: Map<Long, BookMetadataRecord> = dsl.selectFrom(BOOK_METADATA)
            .where(BOOK_METADATA.BOOK_ID.`in`(bookIds))
            .fetchMap(BOOK_METADATA.BOOK_ID)

        val authorsByBook: Map<Long, List<String>> =
            dsl.select(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, AUTHOR.NAME)
                .from(BOOK_METADATA_AUTHOR_MAPPING)
                .join(AUTHOR).on(AUTHOR.ID.eq(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID))
                .where(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID.`in`(bookIds))
                .orderBy(BOOK_METADATA_AUTHOR_MAPPING.SORT_ORDER)
                .fetch().groupBy({ it.value1()!! }, { it.value2()!! })

        val categoriesByBook: Map<Long, Set<String>> =
            dsl.select(BOOK_METADATA_CATEGORY_MAPPING.BOOK_ID, CATEGORY.NAME)
                .from(BOOK_METADATA_CATEGORY_MAPPING)
                .join(CATEGORY).on(CATEGORY.ID.eq(BOOK_METADATA_CATEGORY_MAPPING.CATEGORY_ID))
                .where(BOOK_METADATA_CATEGORY_MAPPING.BOOK_ID.`in`(bookIds))
                .fetch().groupBy({ it.value1()!! }, { it.value2()!! }).mapValues { it.value.toSet() }

        val moodsByBook: Map<Long, Set<String>> =
            dsl.select(BOOK_METADATA_MOOD_MAPPING.BOOK_ID, MOOD.NAME)
                .from(BOOK_METADATA_MOOD_MAPPING)
                .join(MOOD).on(MOOD.ID.eq(BOOK_METADATA_MOOD_MAPPING.MOOD_ID))
                .where(BOOK_METADATA_MOOD_MAPPING.BOOK_ID.`in`(bookIds))
                .fetch().groupBy({ it.value1()!! }, { it.value2()!! }).mapValues { it.value.toSet() }

        val tagsByBook: Map<Long, Set<String>> =
            dsl.select(BOOK_METADATA_TAG_MAPPING.BOOK_ID, TAG.NAME)
                .from(BOOK_METADATA_TAG_MAPPING)
                .join(TAG).on(TAG.ID.eq(BOOK_METADATA_TAG_MAPPING.TAG_ID))
                .where(BOOK_METADATA_TAG_MAPPING.BOOK_ID.`in`(bookIds))
                .fetch().groupBy({ it.value1()!! }, { it.value2()!! }).mapValues { it.value.toSet() }

        val shelvesByBook = shelvesByBook(bookIds)
        val filesByBook = filesByBook(bookIds)

        return scalars.map { s ->
            val files = filesByBook[s.id].orEmpty()
            val primary = primaryFile(s.formatPriority, files)
            Book.builder()
                .id(s.id)
                .libraryId(s.libraryId)
                .libraryName(s.libraryName)
                .addedOn(s.addedOn?.toInstant())
                .metadataMatchScore(s.metadataMatchScore?.toFloat())
                .isPhysical(s.isPhysical == 1.toByte())
                .libraryPath(s.libraryPathId?.let { LibraryPath.builder().id(it).build() })
                .metadata(metaByBook[s.id]?.let { buildMetadata(it, authorsByBook[s.id].orEmpty(), categoriesByBook[s.id].orEmpty(), moodsByBook[s.id].orEmpty(), tagsByBook[s.id].orEmpty()) })
                .shelves(shelvesByBook[s.id]?.toSet())
                .primaryFile(primary?.let { toBookFile(s, it) })
                .alternativeFormats(files.filter { it.isBook && it != primary }.map { toBookFile(s, it) })
                .supplementaryFiles(files.filter { !it.isBook }.map { toBookFile(s, it) })
                .build()
        }
    }

    // ------------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------------

    private fun buildMetadata(
        m: BookMetadataRecord,
        authors: List<String>, categories: Set<String>, moods: Set<String>, tags: Set<String>
    ): BookMetadata =
        BookMetadata.builder()
            .bookId(m.bookId)
            .title(m.title)
            .subtitle(m.subtitle)
            .publisher(m.publisher)
            .publishedDate(m.publishedDate)
            .description(m.description)
            .seriesName(m.seriesName)
            .seriesNumber(m.seriesNumber?.toFloat())
            .seriesTotal(m.seriesTotal)
            .isbn13(m.isbn_13)
            .isbn10(m.isbn_10)
            .pageCount(m.pageCount)
            .language(m.language)
            .narrator(m.narrator)
            .abridged(m.abridged.toBool())
            .asin(m.asin)
            .amazonRating(m.amazonRating)
            .amazonReviewCount(m.amazonReviewCount)
            .goodreadsId(m.goodreadsId)
            .comicvineId(m.comicvineId)
            .goodreadsRating(m.goodreadsRating)
            .goodreadsReviewCount(m.goodreadsReviewCount)
            .hardcoverId(m.hardcoverId)
            .hardcoverBookId(m.hardcoverBookId)
            .hardcoverRating(m.hardcoverRating)
            .hardcoverReviewCount(m.hardcoverReviewCount)
            .lubimyczytacRating(m.lubimyczytacRating)
            .googleId(m.googleId)
            .lubimyczytacId(m.lubimyczytacId)
            .ranobedbId(m.ranobedbId)
            .ranobedbRating(m.ranobedbRating)
            .audibleId(m.audibleId)
            .audibleRating(m.audibleRating)
            .audibleReviewCount(m.audibleReviewCount)
            .coverUpdatedOn(m.coverUpdatedOn?.toInstant())
            .audiobookCoverUpdatedOn(m.audiobookCoverUpdatedOn?.toInstant())
            .rating(m.rating)
            .ageRating(m.ageRating)
            .contentRating(m.contentRating)
            .authors(authors)
            .categories(categories)
            .moods(moods)
            .tags(tags)
            .titleLocked(m.titleLocked.toBool())
            .subtitleLocked(m.subtitleLocked.toBool())
            .publisherLocked(m.publisherLocked.toBool())
            .publishedDateLocked(m.publishedDateLocked.toBool())
            .descriptionLocked(m.descriptionLocked.toBool())
            .seriesNameLocked(m.seriesNameLocked.toBool())
            .seriesNumberLocked(m.seriesNumberLocked.toBool())
            .seriesTotalLocked(m.seriesTotalLocked.toBool())
            .isbn13Locked(m.isbn_13Locked.toBool())
            .isbn10Locked(m.isbn_10Locked.toBool())
            .asinLocked(m.asinLocked.toBool())
            .goodreadsIdLocked(m.goodreadsIdLocked.toBool())
            .comicvineIdLocked(m.comicvineIdLocked.toBool())
            .hardcoverIdLocked(m.hardcoverIdLocked.toBool())
            .hardcoverBookIdLocked(m.hardcoverBookIdLocked.toBool())
            .googleIdLocked(m.googleIdLocked.toBool())
            .pageCountLocked(m.pageCountLocked.toBool())
            .languageLocked(m.languageLocked.toBool())
            .amazonRatingLocked(m.amazonRatingLocked.toBool())
            .amazonReviewCountLocked(m.amazonReviewCountLocked.toBool())
            .goodreadsRatingLocked(m.goodreadsRatingLocked.toBool())
            .goodreadsReviewCountLocked(m.goodreadsReviewCountLocked.toBool())
            .hardcoverRatingLocked(m.hardcoverRatingLocked.toBool())
            .hardcoverReviewCountLocked(m.hardcoverReviewCountLocked.toBool())
            .lubimyczytacIdLocked(m.lubimyczytacIdLocked.toBool())
            .lubimyczytacRatingLocked(m.lubimyczytacRatingLocked.toBool())
            .ranobedbIdLocked(m.ranobedbIdLocked.toBool())
            .ranobedbRatingLocked(m.ranobedbRatingLocked.toBool())
            .audibleIdLocked(m.audibleIdLocked.toBool())
            .audibleRatingLocked(m.audibleRatingLocked.toBool())
            .audibleReviewCountLocked(m.audibleReviewCountLocked.toBool())
            .coverLocked(m.coverLocked.toBool())
            .audiobookCoverLocked(m.audiobookCoverLocked.toBool())
            .authorsLocked(m.authorsLocked.toBool())
            .categoriesLocked(m.categoriesLocked.toBool())
            .moodsLocked(m.moodsLocked.toBool())
            .tagsLocked(m.tagsLocked.toBool())
            .reviewsLocked(m.reviewsLocked.toBool())
            .narratorLocked(m.narratorLocked.toBool())
            .abridgedLocked(m.abridgedLocked.toBool())
            .ageRatingLocked(m.ageRatingLocked.toBool())
            .contentRatingLocked(m.contentRatingLocked.toBool())
            .build()

    // ------------------------------------------------------------------------
    // Shelves
    // ------------------------------------------------------------------------

    private fun shelvesByBook(bookIds: Collection<Long>): Map<Long, List<Shelf>> {
        val bookCount = field(
            selectCount().from(BOOK_SHELF_MAPPING).where(BOOK_SHELF_MAPPING.SHELF_ID.eq(SHELF.ID))
        )
        return dsl.select(
            BOOK_SHELF_MAPPING.BOOK_ID, SHELF.ID, SHELF.NAME, SHELF.ICON, SHELF.ICON_TYPE,
            SHELF.SORT, SHELF.USER_ID, SHELF.IS_PUBLIC, bookCount
        )
            .from(BOOK_SHELF_MAPPING)
            .join(SHELF).on(SHELF.ID.eq(BOOK_SHELF_MAPPING.SHELF_ID))
            .where(BOOK_SHELF_MAPPING.BOOK_ID.`in`(bookIds))
            .fetchGroups(BOOK_SHELF_MAPPING.BOOK_ID) { r ->
                Shelf.builder()
                    .id(r[SHELF.ID])
                    .name(r[SHELF.NAME])
                    .icon(r[SHELF.ICON])
                    .iconType(r[SHELF.ICON_TYPE]?.let { IconType.valueOf(it) })
                    .sort(null)
                    .userId(r[SHELF.USER_ID])
                    .publicShelf(r[SHELF.IS_PUBLIC] == 1.toByte())
                    .bookCount(r[bookCount])
                    .build()
            }
    }

    // ------------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------------

    private fun filesByBook(bookIds: Collection<Long>): Map<Long, List<FileRow>> =
        dsl.select(
            BOOK_FILE.BOOK_ID, BOOK_FILE.ID, BOOK_FILE.FILE_NAME, BOOK_FILE.FILE_SUB_PATH,
            BOOK_FILE.IS_BOOK, BOOK_FILE.IS_FOLDER_BASED, BOOK_FILE.BOOK_TYPE, BOOK_FILE.ARCHIVE_TYPE,
            BOOK_FILE.FILE_SIZE_KB, BOOK_FILE.DESCRIPTION, BOOK_FILE.ADDED_ON
        )
            .from(BOOK_FILE)
            .where(BOOK_FILE.BOOK_ID.`in`(bookIds))
            .orderBy(BOOK_FILE.ID)
            .fetchGroups(BOOK_FILE.BOOK_ID) { r ->
                FileRow(
                    id = r[BOOK_FILE.ID]!!,
                    fileName = r[BOOK_FILE.FILE_NAME],
                    fileSubPath = r[BOOK_FILE.FILE_SUB_PATH],
                    isBook = r[BOOK_FILE.IS_BOOK] == 1.toByte(),
                    folderBased = r[BOOK_FILE.IS_FOLDER_BASED] == 1.toByte(),
                    bookType = r[BOOK_FILE.BOOK_TYPE],
                    archiveType = r[BOOK_FILE.ARCHIVE_TYPE],
                    fileSizeKb = r[BOOK_FILE.FILE_SIZE_KB],
                    description = r[BOOK_FILE.DESCRIPTION],
                    addedOn = r[BOOK_FILE.ADDED_ON]
                )
            }

    /** Mirrors BookMapperV2.getPrimaryBookFile: first book-format file, honoring library format priority. */
    private fun primaryFile(formatPriorityJson: String?, files: List<FileRow>): FileRow? {
        val bookFormats = files.filter { it.isBook }
        if (bookFormats.isEmpty()) return null
        for (format in parseFormatPriority(formatPriorityJson)) {
            val match = bookFormats.firstOrNull { it.bookType == format }
            if (match != null) return match
        }
        return bookFormats.first()
    }

    private fun toBookFile(s: BookScalars, f: FileRow): BookFile =
        BookFile.builder()
            .id(f.id)
            .bookId(s.id)
            .fileName(f.fileName)
            .filePath(fullFilePath(s.libraryPathValue, f.fileSubPath, f.fileName))
            .fileSubPath(f.fileSubPath)
            .isBook(f.isBook)
            .bookType(f.bookType?.let { BookFileType.valueOf(it) })
            .archiveType(f.archiveType?.let { ArchiveUtils.ArchiveType.valueOf(it) })
            .fileSizeKb(f.fileSizeKb)
            .extension(extensionOf(f))
            .description(f.description)
            .addedOn(f.addedOn?.toInstant())
            .build()

    private fun fullFilePath(libraryPath: String?, subPath: String?, fileName: String?): String? {
        if (libraryPath == null || subPath == null || fileName == null) return null
        return Paths.get(libraryPath, subPath, fileName).toString()
    }

    /** Non-folder-based: extension from file name. Folder-based extension is filesystem-derived (not reproduced). */
    private fun extensionOf(f: FileRow): String? {
        if (f.folderBased) return null
        val name = f.fileName ?: return null
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(dot + 1).lowercase() else null
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private fun parseFormatPriority(json: String?): List<String> {
        if (json.isNullOrEmpty()) return emptyList()
        return Regex("\"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.toList()
    }

    private fun Byte?.toBool(): Boolean? = this?.let { it == 1.toByte() }

    private fun LocalDateTime.toInstant(): Instant = this.toInstant(ZoneOffset.UTC)
}
