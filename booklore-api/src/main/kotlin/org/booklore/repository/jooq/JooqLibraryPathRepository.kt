package org.booklore.repository.jooq

import org.booklore.jooq.tables.LibraryPath.LIBRARY_PATH
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
