package org.booklore.repository.jooq

import org.booklore.jooq.tables.LibraryPath.LIBRARY_PATH
import org.booklore.jooq.tables.records.LibraryPathRecord
import org.booklore.model.entity.LibraryEntity
import org.booklore.model.entity.LibraryPathEntity
import org.booklore.repository.jooq.dto.LibraryPathRow
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/**
 * jOOQ reads for library_path. Exists so callers can resolve a library's paths WITHOUT navigating the
 * LAZY LibraryEntity.libraryPaths collection, which throws LazyInitializationException outside a
 * transaction now that open-in-view is false.
 */
@Repository
class JooqLibraryPathRepository(private val dsl: DSLContext) {

    /** Mirrors LibraryPathRepository's inherited/derived methods. `library` is left null - traced every
     * consumer of findById/findByLibraryId and none dereferences it, unlike findAllWithLibrary below. */
    private fun toEntity(r: LibraryPathRecord): LibraryPathEntity =
        LibraryPathEntity.builder().id(r.id).path(r.path).build()

    fun findById(id: Long): LibraryPathEntity? =
        dsl.selectFrom(LIBRARY_PATH).where(LIBRARY_PATH.ID.eq(id)).fetchOne()?.let(::toEntity)

    fun findByLibraryId(libraryId: Long): List<LibraryPathEntity> =
        dsl.selectFrom(LIBRARY_PATH)
            .where(LIBRARY_PATH.LIBRARY_ID.eq(libraryId))
            .fetch()
            .map(::toEntity)

    /** Mirrors LibraryPathRepository.findAllWithLibrary: the one consumer (LibraryHealthService) only
     * ever reads library.getId() off the result, so library is a minimal id-only stub - not a full
     * LIBRARY join. */
    fun findAllWithLibrary(): List<LibraryPathEntity> =
        dsl.selectFrom(LIBRARY_PATH)
            .fetch()
            .map { r -> toEntity(r).also { it.library = LibraryEntity.builder().id(r.libraryId).build() } }

    fun deleteAllById(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        dsl.deleteFrom(LIBRARY_PATH).where(LIBRARY_PATH.ID.`in`(ids)).execute()
    }

    /** Mirrors LibraryPathRepository.save(path) for a single new path; returns the generated id. */
    fun insertLibraryPath(libraryId: Long, path: String): Long =
        dsl.insertInto(LIBRARY_PATH)
            .set(LIBRARY_PATH.LIBRARY_ID, libraryId)
            .set(LIBRARY_PATH.PATH, path)
            .returning(LIBRARY_PATH.ID)
            .fetchOne()!!.id!!

    /** Mirrors LibraryPathRepository.saveAll for a batch of new paths; no id feedback (no consumer
     * needs the generated ids back on the passed-in entities). */
    fun insertAll(paths: List<LibraryPathEntity>) {
        if (paths.isEmpty()) return
        val rows = paths.map { p ->
            dsl.newRecord(LIBRARY_PATH).apply {
                libraryId = p.library.id
                path = p.path
            }
        }
        dsl.batchInsert(rows).execute()
    }

    /** The path of [pathId], but only if it belongs to [libraryId]; null otherwise. */
    fun findPathByIdAndLibraryId(pathId: Long, libraryId: Long): String? =
        dsl.select(LIBRARY_PATH.PATH)
            .from(LIBRARY_PATH)
            .where(LIBRARY_PATH.ID.eq(pathId))
            .and(LIBRARY_PATH.LIBRARY_ID.eq(libraryId))
            .fetchOne(LIBRARY_PATH.PATH)

    /** All configured paths of [libraryId], ordered by id. Rows with a null path are skipped. */
    fun findPathsByLibraryId(libraryId: Long): List<LibraryPathRow> =
        dsl.select(LIBRARY_PATH.ID, LIBRARY_PATH.PATH)
            .from(LIBRARY_PATH)
            .where(LIBRARY_PATH.LIBRARY_ID.eq(libraryId))
            .orderBy(LIBRARY_PATH.ID)
            .fetch()
            .mapNotNull { r -> r.value2()?.let { LibraryPathRow(r.value1(), it) } }

    /** Paths for several libraries at once, grouped by library id (for list endpoints). */
    fun findPathsByLibraryIds(libraryIds: Collection<Long>): Map<Long, List<LibraryPathRow>> {
        if (libraryIds.isEmpty()) return emptyMap()
        return dsl.select(LIBRARY_PATH.LIBRARY_ID, LIBRARY_PATH.ID, LIBRARY_PATH.PATH)
            .from(LIBRARY_PATH)
            .where(LIBRARY_PATH.LIBRARY_ID.`in`(libraryIds))
            .orderBy(LIBRARY_PATH.ID)
            .fetch()
            .mapNotNull { r ->
                val libId = r.value1() ?: return@mapNotNull null
                val path = r.value3() ?: return@mapNotNull null
                libId to LibraryPathRow(r.value2(), path)
            }
            .groupBy({ it.first }, { it.second })
    }

    /** Number of configured paths for [libraryId]. */
    fun countPathsByLibraryId(libraryId: Long): Int =
        dsl.fetchCount(LIBRARY_PATH, LIBRARY_PATH.LIBRARY_ID.eq(libraryId))
}
