package org.booklore.repository.jooq

import org.booklore.jooq.tables.UserLibraryMapping.USER_LIBRARY_MAPPING
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqUserLibraryRepository(private val dsl: DSLContext) {

    private val ulm = USER_LIBRARY_MAPPING

    fun findLibraryIdsByUserId(userId: Long): Set<Long> =
        dsl.select(ulm.LIBRARY_ID)
            .from(ulm)
            .where(ulm.USER_ID.eq(userId))
            .fetchSet(ulm.LIBRARY_ID)

    fun findUserIdsByLibraryId(libraryId: Long): Set<Long> =
        dsl.select(ulm.USER_ID)
            .from(ulm)
            .where(ulm.LIBRARY_ID.eq(libraryId))
            .fetchSet(ulm.USER_ID)

    /** Replaces the full set of libraries assigned to [userId] with exactly [libraryIds]. */
    fun setLibrariesForUser(userId: Long, libraryIds: Collection<Long>) {
        val existing = findLibraryIdsByUserId(userId)
        val wanted = libraryIds.toSet()
        val toRemove = existing - wanted
        val toAdd = wanted - existing
        if (toRemove.isNotEmpty()) {
            dsl.deleteFrom(ulm)
                .where(ulm.USER_ID.eq(userId).and(ulm.LIBRARY_ID.`in`(toRemove)))
                .execute()
        }
        toAdd.forEach { libraryId ->
            dsl.insertInto(ulm).set(ulm.USER_ID, userId).set(ulm.LIBRARY_ID, libraryId).execute()
        }
    }
}
