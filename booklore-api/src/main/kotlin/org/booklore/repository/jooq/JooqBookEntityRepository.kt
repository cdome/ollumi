package org.booklore.repository.jooq

import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.ComicMetadata.COMIC_METADATA
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.LibraryPath.LIBRARY_PATH
import org.booklore.jooq.tables.records.BookFileRecord
import org.booklore.jooq.tables.records.BookMetadataRecord
import org.booklore.jooq.tables.records.BookRecord
import org.booklore.jooq.tables.records.ComicMetadataRecord
import org.booklore.jooq.tables.records.LibraryPathRecord
import org.booklore.jooq.tables.records.LibraryRecord
import org.booklore.model.dto.BookRecommendationLite
import org.booklore.model.dto.Sort
import org.booklore.model.entity.BookEntity
import org.booklore.model.entity.BookFileEntity
import org.booklore.model.entity.BookMetadataEntity
import org.booklore.model.entity.ComicMetadataEntity
import org.booklore.model.entity.LibraryEntity
import org.booklore.model.entity.LibraryPathEntity
import org.booklore.model.enums.BookFileType
import org.booklore.model.enums.IconType
import org.booklore.model.enums.LibraryOrganizationMode
import org.booklore.model.enums.MetadataSource
import org.booklore.model.enums.SortDirection
import org.booklore.util.ArchiveUtils
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Reconstructs mutable BookEntity object graphs from jOOQ, replacing BookRepository's remaining
 * @EntityGraph/native-query finder methods (the last JPA read surface before BookEntity/Hibernate/
 * H2 can be dropped entirely - Tier 2 already migrated every write). The entities returned here are
 * PLAIN, never-Hibernate-managed objects, built via .builder() and never passed to
 * bookRepository.save/persist/getReferenceById - safe to mutate freely, since the
 * enableAssociationManagement bytecode trap (Tier 2, slice 2c) only fires on objects tracked by an
 * active persistence context. Each public method mirrors one BookRepository finder 1:1 so call
 * sites can be swapped without touching downstream .getMetadata()/.getBookFiles() navigation.
 *
 * Sub-graph shape per method is chosen by tracing each finder's actual consumer(s), not just its
 * declared @EntityGraph attributePaths - a declared graph is only a reliable lower bound; a
 * consumer inside an active transaction can still touch a lazy field beyond it (this happened
 * here: findDeletedByLibraryId's graph omits metadata, but its consumer maps through BookMapper,
 * which needs it). Where a consumer passes the book into a mapper/writer whose own field usage
 * wasn't fully traced, the full graph (metadata+comicMetadata+bookFiles+library+libraryPath) is
 * populated rather than risk a silent gap - over-fetching here is harmless, unlike under-fetching.
 */
@Repository
class JooqBookEntityRepository(private val dsl: DSLContext, private val objectMapper: ObjectMapper) {

    private val bm = BOOK_METADATA
    private val bf = BOOK_FILE
    private val cm = COMIC_METADATA

    private val chapterListType = object : TypeReference<List<BookFileEntity.AudioFileChapter>>() {}
    private val formatPriorityType = object : TypeReference<List<BookFileType>>() {}

    // ========================================================================
    // Public finders - one per migrated BookRepository method
    // ========================================================================

    /** Mirrors findByIdFull: declared graph is metadata + metadata.comicMetadata + bookFiles only, but
     * BookMetadataService.processSingleBookUpdate's notification path maps the result through
     * BookMapper (needs library + libraryPath too, for BookFileEntity.getFullFilePath()) - full graph. */
    fun findByIdFull(id: Long): BookEntity? {
        val books = fetchBase(BOOK.ID.eq(id).and(BookConditions.notDeleted()))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books[id]
    }

    /** Mirrors findByIdWithMetadata: declared graph is metadata + metadata.comicMetadata + library, but
     * BookReviewService.getByBookId/refreshReviews route the result through
     * MetadataRefreshService.fetchMetadataForBook(BookEntity), which calls bookMapper.toBook(entity)
     * directly - needs the full graph (bookFiles + libraryPath too). */
    fun findByIdWithMetadata(id: Long): BookEntity? {
        val books = fetchBase(BOOK.ID.eq(id).and(BookConditions.notDeleted()))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books[id]
    }

    /** Mirrors findByCurrentHashIncludingRecentlyDeleted: declared graph is bookFiles only (JOIN FETCH),
     * but both consumers (LibraryFileEventProcessor, BookFileTransactionalHandler) pass the result into
     * BookFilePersistenceService.updatePathIfChanged, which reads book.getLibraryPath()/book.getLibrary()
     * directly and finishes with bookMapper.toBookWithDescription(book, false) - full graph. */
    fun findByCurrentHashIncludingRecentlyDeleted(currentHash: String, cutoff: Instant): BookEntity? {
        val cutoffLocal = LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC)
        val bookId = dsl.select(bf.BOOK_ID)
            .from(bf)
            .join(BOOK).on(BOOK.ID.eq(bf.BOOK_ID))
            .where(
                bf.CURRENT_HASH.eq(currentHash)
                    .and(bf.IS_BOOK.eq(1))
                    .and(BookConditions.notDeleted().or(BOOK.DELETED_AT.gt(cutoffLocal)))
            )
            .fetchOne(bf.BOOK_ID) ?: return null
        val books = fetchBase(BOOK.ID.eq(bookId))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books[bookId]
    }

    /** Mirrors findAllByLibraryPathIdAndFileSubPath: no declared graph at all (plain JPQL), but both
     * consumers (BookFileTransactionalHandler, BookGroupingService) read metadata.title, bookFiles,
     * library and libraryPath while fuzzy-matching candidate books - full graph. */
    fun findAllByLibraryPathIdAndFileSubPath(libraryPathId: Long, fileSubPath: String): List<BookEntity> {
        val bookIds = dsl.selectDistinct(bf.BOOK_ID)
            .from(bf)
            .join(BOOK).on(BOOK.ID.eq(bf.BOOK_ID))
            .where(
                BOOK.LIBRARY_PATH_ID.eq(libraryPathId)
                    .and(bf.FILE_SUB_PATH.eq(fileSubPath))
                    .and(bf.IS_BOOK.eq(1))
                    .and(BookConditions.notDeleted())
            )
            .fetch(bf.BOOK_ID)
        val books = fetchBase(BOOK.ID.`in`(bookIds))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Mirrors findFilelessBooksByLibraryId (JPQL: LEFT JOIN b.bookFiles GROUP BY b HAVING COUNT(bf) = 0).
     * Full graph - consumers read metadata.title, library and libraryPath while matching a file to a
     * fileless book (bookFiles is always empty by construction here, but attached for shape consistency). */
    fun findFilelessBooksByLibraryId(libraryId: Long): List<BookEntity> {
        val books = fetchBase(
            BOOK.LIBRARY_ID.eq(libraryId)
                .and(BookConditions.notDeleted())
                .and(org.jooq.impl.DSL.notExists(dsl.selectOne().from(bf).where(bf.BOOK_ID.eq(BOOK.ID))))
        )
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Mirrors findAllWithMetadataByLibraryId: same query/graph shape as findAllByLibraryIdWithFiles
     * (both declared the identical full JPA @EntityGraph over the identical WHERE clause). */
    fun findAllWithMetadataByLibraryId(libraryId: Long): List<BookEntity> = findAllByLibraryIdWithFiles(libraryId)

    /** Mirrors findBooksForMigrationBatch: base scalars only. Both consumers
     * (GenerateCoverHashMigration, PopulateSearchTextMigration) only read id/bookCoverHash off the
     * batch itself - PopulateSearchTextMigration re-fetches full entities via a separate findAllById
     * call for the metadata it actually needs. */
    fun findBooksForMigrationBatch(offset: Int, limit: Int): List<BookEntity> =
        dsl.selectFrom(BOOK)
            .where(BookConditions.notDeleted())
            .orderBy(BOOK.ID.asc())
            .limit(limit)
            .offset(offset)
            .fetch()
            .map { toBookEntity(it) }

    /** Mirrors findByCurrentHash: bookFiles + metadata + library + libraryPath (+ comicMetadata, safe superset). */
    fun findByCurrentHash(currentHash: String): BookEntity? {
        val bookId = dsl.select(bf.BOOK_ID)
            .from(bf)
            .join(BOOK).on(BOOK.ID.eq(bf.BOOK_ID))
            .where(bf.CURRENT_HASH.eq(currentHash).and(bf.IS_BOOK.eq(1)).and(BookConditions.notDeleted()))
            .fetchOne(bf.BOOK_ID) ?: return null
        val books = fetchBase(BOOK.ID.eq(bookId))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books[bookId]
    }

    /** Mirrors findDeletedByLibraryId: full graph (declared graph is libraryPath+bookFiles only, but
     * the consumer maps through BookMapper, which needs metadata too - see class doc). */
    fun findDeletedByLibraryId(libraryId: Long): List<BookEntity> {
        val books = fetchBase(BOOK.LIBRARY_ID.eq(libraryId).and(BOOK.DELETED.eq(1)))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Mirrors findAllWithMetadata: full graph, every non-deleted book. */
    fun findAllWithMetadata(): List<BookEntity> {
        val books = fetchBase(BookConditions.notDeleted())
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Mirrors findAllByLibraryIdWithFiles: full graph, scoped to one library. */
    fun findAllByLibraryIdWithFiles(libraryId: Long): List<BookEntity> {
        val books = fetchBase(BOOK.LIBRARY_ID.eq(libraryId).and(BookConditions.notDeleted()))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Mirrors findAllForDuplicateDetection: full graph, scoped to one library. */
    fun findAllForDuplicateDetection(libraryId: Long): List<BookEntity> {
        val books = fetchBase(BOOK.LIBRARY_ID.eq(libraryId).and(BookConditions.notDeleted()))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Mirrors findAllWithMetadataByFileSizeKbIsNull: full graph, filtered by any book-format file
     * missing its size. */
    fun findAllWithMetadataByFileSizeKbIsNull(): List<BookEntity> {
        val bookIds = dsl.selectDistinct(bf.BOOK_ID)
            .from(bf)
            .join(BOOK).on(BOOK.ID.eq(bf.BOOK_ID))
            .where(bf.IS_BOOK.eq(1).and(bf.FILE_SIZE_KB.isNull).and(BookConditions.notDeleted()))
            .fetch(bf.BOOK_ID)
        val books = fetchBase(BOOK.ID.`in`(bookIds))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Mirrors findAllWithMetadataByIds: full graph (identical declared JPA @EntityGraph). Order isn't
     * contractually guaranteed by the original JPQL `WHERE id IN :bookIds` either - callers that need a
     * specific order already re-sort/re-key by id downstream. */
    fun findAllWithMetadataByIds(bookIds: Set<Long>): List<BookEntity> {
        if (bookIds.isEmpty()) return emptyList()
        val books = fetchBase(BOOK.ID.`in`(bookIds).and(BookConditions.notDeleted()))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Mirrors BookMetadataRepository.findAllById: unlike findAllWithMetadataByIds, deliberately has NO
     * notDeleted filter - MetadataManagementService's bulk consolidate/delete operations must keep a
     * soft-deleted book's metadata (search_text, series/publisher/language columns) in sync too, so a
     * later restore doesn't surface stale data. Full graph, since writeMetadataToFile reads
     * metadata.getBook().getPrimaryBookFile()/getFullFilePath() and feeds the book into
     * FileMoveService.moveSingleFile (needs library+libraryPath+bookFiles). */
    fun findAllByIdsIncludingDeleted(bookIds: Set<Long>): List<BookEntity> {
        if (bookIds.isEmpty()) return emptyList()
        val books = fetchBase(BOOK.ID.`in`(bookIds))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Single-id convenience wrapper, for consumers (PendingDeletionPool.recoverBook,
     * BookFilePersistenceService.recoverFolderBook) that need a possibly-soft-deleted book back with
     * the full graph - both feed the result into bookMapper.toBookWithDescription. */
    fun findByIdIncludingDeleted(id: Long): BookEntity? = findAllByIdsIncludingDeleted(setOf(id)).firstOrNull()

    // ========================================================================
    // Replacing BookRepository's inherited JpaRepository methods (existsById/count) - the interface
    // itself never had any custom methods, just these three inherited CRUD shapes still called directly.
    // ========================================================================

    fun existsById(id: Long): Boolean = dsl.fetchExists(BOOK, BOOK.ID.eq(id))

    /** Mirrors bookRepository.count(): unfiltered row count, deleted books included (matches plain
     * JpaRepository.count() semantics - TelemetryService reports this as a raw total). */
    fun countAllBooks(): Long = dsl.fetchCount(BOOK).toLong()

    // ========================================================================
    // BookFileEntity finders - replacing BookFileRepository/BookAdditionalFileRepository (both were
    // JpaRepository<BookFileEntity, Long> over the same table; consolidated here since consumers
    // consistently touch bookFile.getBook() with real relation depth - .getDeleted(), .getLibraryPath(),
    // .getLibrary() - not just a null check, so every method attaches the FULL parent BookEntity rather
    // than tracing each consumer's exact depth. No notDeleted filter anywhere below, matching every
    // original JPQL query here (none had one) - these operate on files, not the book lifecycle.
    // ========================================================================

    private fun hydrateBookFiles(records: List<BookFileRecord>): List<BookFileEntity> {
        if (records.isEmpty()) return emptyList()
        val bookIds = records.mapNotNull { it.bookId }.distinct().toSet()
        val booksById = findAllByIdsIncludingDeleted(bookIds).associateBy { it.id }
        return records.mapNotNull { r -> booksById[r.bookId]?.let { book -> toBookFileEntity(r, book) } }
    }

    /** Mirrors both BookFileRepository.findById and BookAdditionalFileRepository.findById (inherited
     * JpaRepository methods) and BookFileRepository.findByIdWithBookAndLibraryPath (whose declared
     * graph - book+libraryPath+library - is a strict subset of this). */
    fun findBookFileById(id: Long): BookFileEntity? {
        val record = dsl.selectFrom(bf).where(bf.ID.eq(id)).fetchOne() ?: return null
        return hydrateBookFiles(listOf(record)).firstOrNull()
    }

    fun countBookFilesByBookId(bookId: Long): Long = dsl.fetchCount(bf, bf.BOOK_ID.eq(bookId)).toLong()

    fun countAllBookFiles(): Long = dsl.fetchCount(bf).toLong()

    /** Mirrors both BookFileRepository.findByLibraryPathIdAndFileSubPathAndFileName and
     * BookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName - identical JPQL
     * declared in two separate JPA repos over the same entity. */
    fun findBookFileByLibraryPathIdAndFileSubPathAndFileName(libraryPathId: Long, fileSubPath: String, fileName: String): BookFileEntity? {
        val record = dsl.selectFrom(bf)
            .where(
                bf.FILE_SUB_PATH.eq(fileSubPath)
                    .and(bf.FILE_NAME.eq(fileName))
                    .and(bf.BOOK_ID.`in`(dsl.select(BOOK.ID).from(BOOK).where(BOOK.LIBRARY_PATH_ID.eq(libraryPathId))))
            )
            .fetchOne() ?: return null
        return hydrateBookFiles(listOf(record)).firstOrNull()
    }

    fun findBookFilesByBookId(bookId: Long): List<BookFileEntity> =
        hydrateBookFiles(dsl.selectFrom(bf).where(bf.BOOK_ID.eq(bookId)).orderBy(bf.ID.asc()).fetch())

    fun findBookFilesByBookIdAndIsBookFormat(bookId: Long, isBookFormat: Boolean): List<BookFileEntity> =
        hydrateBookFiles(
            dsl.selectFrom(bf)
                .where(bf.BOOK_ID.eq(bookId).and(bf.IS_BOOK.eq(if (isBookFormat) 1 else 0)))
                .orderBy(bf.ID.asc())
                .fetch()
        )

    fun findBookFileByAltFormatCurrentHash(hash: String): BookFileEntity? {
        val record = dsl.selectFrom(bf).where(bf.ALT_FORMAT_CURRENT_HASH.eq(hash)).fetchOne() ?: return null
        return hydrateBookFiles(listOf(record)).firstOrNull()
    }

    fun findBookFilesByLibraryId(libraryId: Long): List<BookFileEntity> =
        hydrateBookFiles(
            dsl.selectFrom(bf)
                .where(bf.BOOK_ID.`in`(dsl.select(BOOK.ID).from(BOOK).where(BOOK.LIBRARY_ID.eq(libraryId))))
                .orderBy(bf.ID.asc())
                .fetch()
        )

    /** Mirrors findAllFullBooks. Declared JPA graph was metadata + metadata.comicMetadata only, and
     * that's genuinely sufficient for MetadataMatchService/BookVectorService/BookSimilarityService/
     * PopulateMetadataScoresMigration (verified: all only read book.getMetadata()/book.getId()) - but
     * this method is reached via the SHARED BookQueryService.getAllFullBookEntities() wrapper, which
     * has 5 callers total, not just those. BookRecommendationService.findSimilarBooks passes its
     * candidates through bookMapper.toBookWithDescription (needs bookFiles+library+libraryPath), and
     * BookCoverService.regenerateCovers filters on book.getPrimaryBookFile() (needs
     * bookFiles+library.formatPriority) - full graph, not the narrower one originally shipped. */
    fun findAllFullBooks(): List<BookEntity> {
        val books = fetchBase(BookConditions.notDeleted())
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Mirrors findBooksWithFilesUnderPath: full graph (feeds PendingDeletionPool's expire/recover
     * paths, which need more than just bookFiles). */
    fun findBooksWithFilesUnderPath(libraryPathId: Long, folderPath: String): List<BookEntity> {
        val prefix = "$folderPath/%"
        val bookIds = dsl.selectDistinct(bf.BOOK_ID)
            .from(bf)
            .join(BOOK).on(BOOK.ID.eq(bf.BOOK_ID))
            .where(
                BOOK.LIBRARY_PATH_ID.eq(libraryPathId)
                    .and(
                        bf.FILE_SUB_PATH.eq(folderPath)
                            .or(bf.FILE_SUB_PATH.like(prefix))
                            .or(bf.IS_FOLDER_BASED.eq(1).and(org.jooq.impl.DSL.concat(bf.FILE_SUB_PATH, org.jooq.impl.DSL.inline("/"), bf.FILE_NAME).eq(folderPath)))
                    )
                    .and(bf.IS_BOOK.eq(1))
                    .and(BookConditions.notDeleted())
            )
            .fetch(bf.BOOK_ID)
        val books = fetchBase(BOOK.ID.`in`(bookIds))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books.values.toList()
    }

    /** Mirrors the native-query findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName. This one's
     * result flows through BookCreatorService.createShellBook's "book already exists" branch into
     * AbstractFileProcessor.createAndMapBook, which unconditionally calls
     * metadataMatchService.calculateMatchScore(entity), bookCreatorService.saveConnections(entity)
     * (which NPEs on a null metadata - metadata.updateSearchText() has no null guard), and finally
     * bookMapper.toBook(entity) - full graph, not just bookFiles+library. */
    fun findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
        libraryId: Long, libraryPathId: Long, fileSubPath: String, fileName: String
    ): BookEntity? {
        val bookId = dsl.select(bf.BOOK_ID)
            .from(bf)
            .join(BOOK).on(BOOK.ID.eq(bf.BOOK_ID))
            .where(
                BOOK.LIBRARY_ID.eq(libraryId)
                    .and(BOOK.LIBRARY_PATH_ID.eq(libraryPathId))
                    .and(bf.FILE_SUB_PATH.eq(fileSubPath))
                    .and(bf.FILE_NAME.eq(fileName))
                    .and(bf.IS_BOOK.eq(1))
            )
            .limit(1)
            .fetchOne(bf.BOOK_ID) ?: return null
        val books = fetchBase(BOOK.ID.eq(bookId))
        attachMetadata(books, includeComic = true)
        attachBookFiles(books)
        attachLibraries(books)
        attachLibraryPaths(books)
        return books[bookId]
    }

    // ========================================================================
    // Base book fetch
    // ========================================================================

    private fun fetchBase(condition: Condition): LinkedHashMap<Long, BookEntity> {
        val result = LinkedHashMap<Long, BookEntity>()
        dsl.selectFrom(BOOK).where(condition).orderBy(BOOK.ID.asc()).fetch().forEach { r ->
            result[r.id!!] = toBookEntity(r)
        }
        return result
    }

    private fun toBookEntity(r: BookRecord): BookEntity =
        BookEntity.builder()
            .id(r.id)
            .metadataMatchScore(r.metadataMatchScore?.toFloat())
            .metadataUpdatedAt(r.metadataUpdatedAt?.toInstantUtc())
            .metadataForWriteUpdatedAt(r.metadataForWriteUpdatedAt?.toInstantUtc())
            .isPhysical(r.isPhysical.toBoolNonNull())
            .addedOn(r.addedOn?.toInstantUtc())
            .scannedOn(r.scannedOn?.toInstantUtc())
            .bookCoverHash(r.bookCoverHash)
            .audiobookCoverHash(r.audiobookCoverHash)
            .deleted(r.deleted.toBoolNonNull())
            .deletedAt(r.deletedAt?.toInstantUtc())
            .similarBooksJson(r.similarBooksJson?.let { objectMapper.readValue(it, object : TypeReference<Set<BookRecommendationLite>>() {}) })
            .bookFiles(ArrayList())
            .build()

    // ========================================================================
    // Attach: library / libraryPath
    // ========================================================================

    private fun attachLibraries(books: Map<Long, BookEntity>) {
        if (books.isEmpty()) return
        val libraryIdByBookId = dsl.select(BOOK.ID, BOOK.LIBRARY_ID)
            .from(BOOK)
            .where(BOOK.ID.`in`(books.keys))
            .fetchMap(BOOK.ID, BOOK.LIBRARY_ID)
        val libraryIds = libraryIdByBookId.values.filterNotNull().distinct()
        if (libraryIds.isEmpty()) return
        val librariesById = dsl.selectFrom(LIBRARY).where(LIBRARY.ID.`in`(libraryIds)).fetch()
            .associate { it.id!! to toLibraryEntity(it) }
        books.forEach { (bookId, book) -> book.library = librariesById[libraryIdByBookId[bookId]] }
    }

    private fun attachLibraryPaths(books: Map<Long, BookEntity>) {
        if (books.isEmpty()) return
        val pathIdByBookId = dsl.select(BOOK.ID, BOOK.LIBRARY_PATH_ID)
            .from(BOOK)
            .where(BOOK.ID.`in`(books.keys))
            .fetchMap(BOOK.ID, BOOK.LIBRARY_PATH_ID)
        val pathIds = pathIdByBookId.values.filterNotNull().distinct()
        val pathsById = if (pathIds.isEmpty()) emptyMap() else
            dsl.selectFrom(LIBRARY_PATH).where(LIBRARY_PATH.ID.`in`(pathIds)).fetch()
                .associate { it.id!! to toLibraryPathEntity(it) }
        books.forEach { (bookId, book) -> book.libraryPath = pathIdByBookId[bookId]?.let { pathsById[it] } }
    }

    private fun toLibraryEntity(r: LibraryRecord): LibraryEntity =
        LibraryEntity.builder()
            .id(r.id)
            .name(r.name)
            .sort(parseSort(r.sort))
            .watch(r.watch.toBoolNonNull())
            .icon(r.icon)
            .iconType(r.iconType?.let { IconType.valueOf(it) })
            .fileNamingPattern(r.fileNamingPattern)
            .formatPriority(r.formatPriority?.let { objectMapper.readValue(it, formatPriorityType) } ?: ArrayList())
            .allowedFormats(r.allowedFormats?.let { objectMapper.readValue(it, formatPriorityType) })
            .organizationMode(r.organizationMode?.let { LibraryOrganizationMode.valueOf(it) })
            .metadataSource(r.metadataSource?.let { MetadataSource.valueOf(it) })
            .build()

    private fun parseSort(raw: String?): Sort? {
        if (raw.isNullOrEmpty()) return null
        val parts = raw.split(",")
        val sort = Sort()
        sort.field = parts[0]
        sort.direction = SortDirection.valueOf(parts[1])
        return sort
    }

    private fun toLibraryPathEntity(r: LibraryPathRecord): LibraryPathEntity =
        LibraryPathEntity.builder()
            .id(r.id)
            .path(r.path)
            .build()

    // ========================================================================
    // Attach: bookFiles
    // ========================================================================

    private fun attachBookFiles(books: Map<Long, BookEntity>) {
        if (books.isEmpty()) return
        val filesByBookId = dsl.selectFrom(bf).where(bf.BOOK_ID.`in`(books.keys)).orderBy(bf.ID.asc()).fetch()
            .groupBy { it.bookId!! }
        books.forEach { (bookId, book) ->
            val files = filesByBookId[bookId].orEmpty().map { toBookFileEntity(it, book) }
            book.bookFiles = ArrayList(files)
        }
    }

    private fun toBookFileEntity(r: BookFileRecord, book: BookEntity): BookFileEntity =
        BookFileEntity.builder()
            .id(r.id)
            .book(book)
            .fileName(r.fileName)
            .fileSubPath(r.fileSubPath)
            .isBookFormat(r.isBook.toBoolNonNull())
            .folderBased(r.isFolderBased.toBoolNonNull())
            .bookType(r.bookType?.let { BookFileType.valueOf(it) })
            .isFixedLayout(r.isFixedLayout.toBoolNonNull())
            .archiveType(r.archiveType?.let { ArchiveUtils.ArchiveType.valueOf(it) })
            .fileSizeKb(r.fileSizeKb)
            .initialHash(r.initialHash)
            .currentHash(r.currentHash)
            .description(r.description)
            .addedOn(r.addedOn?.toInstantUtc())
            .durationSeconds(r.durationSeconds)
            .bitrate(r.bitrate)
            .sampleRate(r.sampleRate)
            .channels(r.channels)
            .codec(r.codec)
            .chapterCount(r.chapterCount)
            .chapters(r.chaptersJson?.takeIf { it.isNotEmpty() }?.let { objectMapper.readValue(it, chapterListType) })
            .build()

    // ========================================================================
    // Attach: metadata (+ optional comicMetadata)
    // ========================================================================

    private fun attachMetadata(books: Map<Long, BookEntity>, includeComic: Boolean) {
        if (books.isEmpty()) return
        val metadataByBookId = dsl.selectFrom(bm).where(bm.BOOK_ID.`in`(books.keys)).fetch()
            .associate { it.bookId!! to toMetadataEntity(it) }
        if (includeComic) {
            val comicByBookId = dsl.selectFrom(cm).where(cm.BOOK_ID.`in`(books.keys)).fetch()
                .associate { it.bookId!! to toComicMetadataEntity(it) }
            metadataByBookId.forEach { (bookId, m) -> m.comicMetadata = comicByBookId[bookId] }
        }
        books.forEach { (bookId, book) ->
            val m = metadataByBookId[bookId]
            m?.book = book
            book.metadata = m
        }
    }

    private fun toMetadataEntity(m: BookMetadataRecord): BookMetadataEntity =
        BookMetadataEntity.builder()
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
            .rating(m.rating)
            .reviewCount(m.reviewCount)
            .coverUpdatedOn(m.coverUpdatedOn?.toInstantUtc())
            .audiobookCoverUpdatedOn(m.audiobookCoverUpdatedOn?.toInstantUtc())
            .amazonRating(m.amazonRating)
            .amazonReviewCount(m.amazonReviewCount)
            .goodreadsRating(m.goodreadsRating)
            .goodreadsReviewCount(m.goodreadsReviewCount)
            .hardcoverRating(m.hardcoverRating)
            .hardcoverReviewCount(m.hardcoverReviewCount)
            .asin(m.asin)
            .goodreadsId(m.goodreadsId)
            .hardcoverId(m.hardcoverId)
            .hardcoverBookId(m.hardcoverBookId)
            .googleId(m.googleId)
            .comicvineId(m.comicvineId)
            .lubimyczytacId(m.lubimyczytacId)
            .lubimyczytacRating(m.lubimyczytacRating)
            .ranobedbId(m.ranobedbId)
            .ranobedbRating(m.ranobedbRating)
            .audibleId(m.audibleId)
            .audibleRating(m.audibleRating)
            .audibleReviewCount(m.audibleReviewCount)
            .narrator(m.narrator)
            .abridged(m.abridged.toBool())
            .embeddingVector(m.embeddingVector)
            .embeddingUpdatedAt(m.embeddingUpdatedAt?.toInstantUtc())
            .searchText(m.searchText)
            .ageRating(m.ageRating)
            .contentRating(m.contentRating)
            .titleLocked(m.titleLocked.toBool())
            .subtitleLocked(m.subtitleLocked.toBool())
            .publisherLocked(m.publisherLocked.toBool())
            .publishedDateLocked(m.publishedDateLocked.toBool())
            .descriptionLocked(m.descriptionLocked.toBool())
            .isbn13Locked(m.isbn_13Locked.toBool())
            .isbn10Locked(m.isbn_10Locked.toBool())
            .asinLocked(m.asinLocked.toBool())
            .pageCountLocked(m.pageCountLocked.toBool())
            .languageLocked(m.languageLocked.toBool())
            .amazonRatingLocked(m.amazonRatingLocked.toBool())
            .amazonReviewCountLocked(m.amazonReviewCountLocked.toBool())
            .goodreadsRatingLocked(m.goodreadsRatingLocked.toBool())
            .goodreadsReviewCountLocked(m.goodreadsReviewCountLocked.toBool())
            .hardcoverRatingLocked(m.hardcoverRatingLocked.toBool())
            .hardcoverReviewCountLocked(m.hardcoverReviewCountLocked.toBool())
            .coverLocked(m.coverLocked.toBool())
            .audiobookCoverLocked(m.audiobookCoverLocked.toBool())
            .seriesNameLocked(m.seriesNameLocked.toBool())
            .seriesNumberLocked(m.seriesNumberLocked.toBool())
            .seriesTotalLocked(m.seriesTotalLocked.toBool())
            .authorsLocked(m.authorsLocked.toBool())
            .categoriesLocked(m.categoriesLocked.toBool())
            .moodsLocked(m.moodsLocked.toBool())
            .tagsLocked(m.tagsLocked.toBool())
            .goodreadsIdLocked(m.goodreadsIdLocked.toBool())
            .hardcoverIdLocked(m.hardcoverIdLocked.toBool())
            .hardcoverBookIdLocked(m.hardcoverBookIdLocked.toBool())
            .googleIdLocked(m.googleIdLocked.toBool())
            .comicvineIdLocked(m.comicvineIdLocked.toBool())
            .lubimyczytacIdLocked(m.lubimyczytacIdLocked.toBool())
            .lubimyczytacRatingLocked(m.lubimyczytacRatingLocked.toBool())
            .ranobedbIdLocked(m.ranobedbIdLocked.toBool())
            .ranobedbRatingLocked(m.ranobedbRatingLocked.toBool())
            .audibleIdLocked(m.audibleIdLocked.toBool())
            .audibleRatingLocked(m.audibleRatingLocked.toBool())
            .audibleReviewCountLocked(m.audibleReviewCountLocked.toBool())
            .reviewsLocked(m.reviewsLocked.toBool())
            .narratorLocked(m.narratorLocked.toBool())
            .abridgedLocked(m.abridgedLocked.toBool())
            .ageRatingLocked(m.ageRatingLocked.toBool())
            .contentRatingLocked(m.contentRatingLocked.toBool())
            .build()

    private fun toComicMetadataEntity(c: ComicMetadataRecord): ComicMetadataEntity =
        ComicMetadataEntity.builder()
            .bookId(c.bookId)
            .issueNumber(c.issueNumber)
            .volumeName(c.volumeName)
            .volumeNumber(c.volumeNumber)
            .storyArc(c.storyArc)
            .storyArcNumber(c.storyArcNumber)
            .alternateSeries(c.alternateSeries)
            .alternateIssue(c.alternateIssue)
            .imprint(c.imprint)
            .format(c.format)
            .blackAndWhite(c.blackAndWhite.toBool())
            .manga(c.manga.toBool())
            .readingDirection(c.readingDirection)
            .webLink(c.webLink)
            .notes(c.notes)
            .issueNumberLocked(c.issueNumberLocked.toBool())
            .volumeNameLocked(c.volumeNameLocked.toBool())
            .volumeNumberLocked(c.volumeNumberLocked.toBool())
            .storyArcLocked(c.storyArcLocked.toBool())
            .storyArcNumberLocked(c.storyArcNumberLocked.toBool())
            .alternateSeriesLocked(c.alternateSeriesLocked.toBool())
            .alternateIssueLocked(c.alternateIssueLocked.toBool())
            .imprintLocked(c.imprintLocked.toBool())
            .formatLocked(c.formatLocked.toBool())
            .blackAndWhiteLocked(c.blackAndWhiteLocked.toBool())
            .mangaLocked(c.mangaLocked.toBool())
            .readingDirectionLocked(c.readingDirectionLocked.toBool())
            .webLinkLocked(c.webLinkLocked.toBool())
            .notesLocked(c.notesLocked.toBool())
            .creatorsLocked(c.creatorsLocked.toBool())
            .pencillersLocked(c.pencillersLocked.toBool())
            .inkersLocked(c.inkersLocked.toBool())
            .coloristsLocked(c.coloristsLocked.toBool())
            .letterersLocked(c.letterersLocked.toBool())
            .coverArtistsLocked(c.coverArtistsLocked.toBool())
            .editorsLocked(c.editorsLocked.toBool())
            .charactersLocked(c.charactersLocked.toBool())
            .teamsLocked(c.teamsLocked.toBool())
            .locationsLocked(c.locationsLocked.toBool())
            .build()

    private fun Byte?.toBool(): Boolean? = this?.let { it == 1.toByte() }
    private fun Byte?.toBoolNonNull(): Boolean = this == 1.toByte()
    private fun LocalDateTime.toInstantUtc(): Instant = this.toInstant(ZoneOffset.UTC)
}
