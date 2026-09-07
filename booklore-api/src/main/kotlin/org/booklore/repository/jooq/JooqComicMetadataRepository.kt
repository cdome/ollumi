package org.booklore.repository.jooq

import org.booklore.jooq.tables.ComicCharacter.COMIC_CHARACTER
import org.booklore.jooq.tables.ComicCreator.COMIC_CREATOR
import org.booklore.jooq.tables.ComicLocation.COMIC_LOCATION
import org.booklore.jooq.tables.ComicMetadata.COMIC_METADATA
import org.booklore.jooq.tables.ComicMetadataCharacterMapping.COMIC_METADATA_CHARACTER_MAPPING
import org.booklore.jooq.tables.ComicMetadataCreatorMapping.COMIC_METADATA_CREATOR_MAPPING
import org.booklore.jooq.tables.ComicMetadataLocationMapping.COMIC_METADATA_LOCATION_MAPPING
import org.booklore.jooq.tables.ComicMetadataTeamMapping.COMIC_METADATA_TEAM_MAPPING
import org.booklore.jooq.tables.ComicTeam.COMIC_TEAM
import org.booklore.model.enums.ComicCreatorRole
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

/**
 * jOOQ read/write access to a book's comic-metadata relation NAMES (characters/teams/locations/
 * creators-by-role), replacing ComicMetadataEntity's @ManyToMany/@OneToMany collections. Junctions
 * key on book_id (== comic_metadata.book_id == book_metadata.book_id, a shared PK). Creators are
 * additionally partitioned by role (six roles share one junction table/mapping entity).
 */
@Repository
class JooqComicMetadataRepository(private val dsl: DSLContext) {

    private val cm = COMIC_METADATA
    private val ccm = COMIC_METADATA_CHARACTER_MAPPING
    private val ctm = COMIC_METADATA_TEAM_MAPPING
    private val clm = COMIC_METADATA_LOCATION_MAPPING
    private val crm = COMIC_METADATA_CREATOR_MAPPING

    // ========================================================================
    // Characters
    // ========================================================================

    fun findCharacterNamesByBookId(bookId: Long): Set<String> =
        dsl.select(COMIC_CHARACTER.NAME)
            .from(ccm).join(COMIC_CHARACTER).on(COMIC_CHARACTER.ID.eq(ccm.CHARACTER_ID))
            .where(ccm.BOOK_ID.eq(bookId))
            .fetchSet(COMIC_CHARACTER.NAME)

    fun clearCharactersForBook(bookId: Long) {
        dsl.deleteFrom(ccm).where(ccm.BOOK_ID.eq(bookId)).execute()
    }

    fun setCharactersForBook(bookId: Long, names: Collection<String>) {
        dsl.deleteFrom(ccm).where(ccm.BOOK_ID.eq(bookId)).execute()
        names.asSequence()
            .filter { it.isNotBlank() }
            .map { upsertCharacterId(it) }
            .distinct()
            .forEach { characterId -> dsl.insertInto(ccm).set(ccm.BOOK_ID, bookId).set(ccm.CHARACTER_ID, characterId).execute() }
    }

    private fun upsertCharacterId(name: String): Long {
        val existing = dsl.select(COMIC_CHARACTER.ID).from(COMIC_CHARACTER).where(COMIC_CHARACTER.NAME.eq(name)).fetchOne(COMIC_CHARACTER.ID)
        if (existing != null) return existing
        return dsl.insertInto(COMIC_CHARACTER).set(COMIC_CHARACTER.NAME, name).returning(COMIC_CHARACTER.ID).fetchOne()!!.id!!
    }

    /** Deletes comic_character rows referenced by no book. Mirrors the JPA deleteOrphaned() cleanup. */
    fun deleteOrphanedCharacters(): Int =
        dsl.deleteFrom(COMIC_CHARACTER)
            .where(DSL.notExists(dsl.selectOne().from(ccm).where(ccm.CHARACTER_ID.eq(COMIC_CHARACTER.ID))))
            .execute()

    // ========================================================================
    // Teams
    // ========================================================================

    fun findTeamNamesByBookId(bookId: Long): Set<String> =
        dsl.select(COMIC_TEAM.NAME)
            .from(ctm).join(COMIC_TEAM).on(COMIC_TEAM.ID.eq(ctm.TEAM_ID))
            .where(ctm.BOOK_ID.eq(bookId))
            .fetchSet(COMIC_TEAM.NAME)

    fun clearTeamsForBook(bookId: Long) {
        dsl.deleteFrom(ctm).where(ctm.BOOK_ID.eq(bookId)).execute()
    }

    fun setTeamsForBook(bookId: Long, names: Collection<String>) {
        dsl.deleteFrom(ctm).where(ctm.BOOK_ID.eq(bookId)).execute()
        names.asSequence()
            .filter { it.isNotBlank() }
            .map { upsertTeamId(it) }
            .distinct()
            .forEach { teamId -> dsl.insertInto(ctm).set(ctm.BOOK_ID, bookId).set(ctm.TEAM_ID, teamId).execute() }
    }

    private fun upsertTeamId(name: String): Long {
        val existing = dsl.select(COMIC_TEAM.ID).from(COMIC_TEAM).where(COMIC_TEAM.NAME.eq(name)).fetchOne(COMIC_TEAM.ID)
        if (existing != null) return existing
        return dsl.insertInto(COMIC_TEAM).set(COMIC_TEAM.NAME, name).returning(COMIC_TEAM.ID).fetchOne()!!.id!!
    }

    fun deleteOrphanedTeams(): Int =
        dsl.deleteFrom(COMIC_TEAM)
            .where(DSL.notExists(dsl.selectOne().from(ctm).where(ctm.TEAM_ID.eq(COMIC_TEAM.ID))))
            .execute()

    // ========================================================================
    // Locations
    // ========================================================================

    fun findLocationNamesByBookId(bookId: Long): Set<String> =
        dsl.select(COMIC_LOCATION.NAME)
            .from(clm).join(COMIC_LOCATION).on(COMIC_LOCATION.ID.eq(clm.LOCATION_ID))
            .where(clm.BOOK_ID.eq(bookId))
            .fetchSet(COMIC_LOCATION.NAME)

    fun clearLocationsForBook(bookId: Long) {
        dsl.deleteFrom(clm).where(clm.BOOK_ID.eq(bookId)).execute()
    }

    fun setLocationsForBook(bookId: Long, names: Collection<String>) {
        dsl.deleteFrom(clm).where(clm.BOOK_ID.eq(bookId)).execute()
        names.asSequence()
            .filter { it.isNotBlank() }
            .map { upsertLocationId(it) }
            .distinct()
            .forEach { locationId -> dsl.insertInto(clm).set(clm.BOOK_ID, bookId).set(clm.LOCATION_ID, locationId).execute() }
    }

    private fun upsertLocationId(name: String): Long {
        val existing = dsl.select(COMIC_LOCATION.ID).from(COMIC_LOCATION).where(COMIC_LOCATION.NAME.eq(name)).fetchOne(COMIC_LOCATION.ID)
        if (existing != null) return existing
        return dsl.insertInto(COMIC_LOCATION).set(COMIC_LOCATION.NAME, name).returning(COMIC_LOCATION.ID).fetchOne()!!.id!!
    }

    fun deleteOrphanedLocations(): Int =
        dsl.deleteFrom(COMIC_LOCATION)
            .where(DSL.notExists(dsl.selectOne().from(clm).where(clm.LOCATION_ID.eq(COMIC_LOCATION.ID))))
            .execute()

    // ========================================================================
    // Creators (partitioned by role — six roles share one mapping table)
    // ========================================================================

    fun findCreatorNamesByBookIdAndRole(bookId: Long, role: ComicCreatorRole): Set<String> =
        dsl.select(COMIC_CREATOR.NAME)
            .from(crm).join(COMIC_CREATOR).on(COMIC_CREATOR.ID.eq(crm.CREATOR_ID))
            .where(crm.BOOK_ID.eq(bookId).and(crm.ROLE.eq(role.name)))
            .fetchSet(COMIC_CREATOR.NAME)

    /** Total creator-mapping row count for a book, across all roles — used for change detection. */
    fun countCreatorMappingsByBookId(bookId: Long): Int =
        dsl.fetchCount(crm, crm.BOOK_ID.eq(bookId))

    fun clearCreatorsForBookRole(bookId: Long, role: ComicCreatorRole) {
        dsl.deleteFrom(crm).where(crm.BOOK_ID.eq(bookId).and(crm.ROLE.eq(role.name))).execute()
    }

    fun setCreatorsForBookRole(bookId: Long, role: ComicCreatorRole, names: Collection<String>) {
        dsl.deleteFrom(crm).where(crm.BOOK_ID.eq(bookId).and(crm.ROLE.eq(role.name))).execute()
        names.asSequence()
            .filter { it.isNotBlank() }
            .map { upsertCreatorId(it) }
            .distinct()
            .forEach { creatorId -> dsl.insertInto(crm).set(crm.BOOK_ID, bookId).set(crm.CREATOR_ID, creatorId).set(crm.ROLE, role.name).execute() }
    }

    private fun upsertCreatorId(name: String): Long {
        val existing = dsl.select(COMIC_CREATOR.ID).from(COMIC_CREATOR).where(COMIC_CREATOR.NAME.eq(name)).fetchOne(COMIC_CREATOR.ID)
        if (existing != null) return existing
        return dsl.insertInto(COMIC_CREATOR).set(COMIC_CREATOR.NAME, name).returning(COMIC_CREATOR.ID).fetchOne()!!.id!!
    }

    fun deleteOrphanedCreators(): Int =
        dsl.deleteFrom(COMIC_CREATOR)
            .where(DSL.notExists(dsl.selectOne().from(crm).where(crm.CREATOR_ID.eq(COMIC_CREATOR.ID))))
            .execute()

    // ========================================================================
    // comic_metadata's own lock columns (replaces ComicMetadataEntity.applyLockToAllFields)
    // ========================================================================

    /** No-op for any bookId with no comic_metadata row - the WHERE clause naturally excludes it. */
    fun lockAllFields(bookIds: Collection<Long>, lock: Boolean) {
        if (bookIds.isEmpty()) return
        val value: Byte = if (lock) 1 else 0
        dsl.update(cm)
            .set(cm.ISSUE_NUMBER_LOCKED, value)
            .set(cm.VOLUME_NAME_LOCKED, value)
            .set(cm.VOLUME_NUMBER_LOCKED, value)
            .set(cm.STORY_ARC_LOCKED, value)
            .set(cm.STORY_ARC_NUMBER_LOCKED, value)
            .set(cm.ALTERNATE_SERIES_LOCKED, value)
            .set(cm.ALTERNATE_ISSUE_LOCKED, value)
            .set(cm.IMPRINT_LOCKED, value)
            .set(cm.FORMAT_LOCKED, value)
            .set(cm.BLACK_AND_WHITE_LOCKED, value)
            .set(cm.MANGA_LOCKED, value)
            .set(cm.READING_DIRECTION_LOCKED, value)
            .set(cm.WEB_LINK_LOCKED, value)
            .set(cm.NOTES_LOCKED, value)
            .set(cm.CREATORS_LOCKED, value)
            .set(cm.PENCILLERS_LOCKED, value)
            .set(cm.INKERS_LOCKED, value)
            .set(cm.COLORISTS_LOCKED, value)
            .set(cm.LETTERERS_LOCKED, value)
            .set(cm.COVER_ARTISTS_LOCKED, value)
            .set(cm.EDITORS_LOCKED, value)
            .set(cm.CHARACTERS_LOCKED, value)
            .set(cm.TEAMS_LOCKED, value)
            .set(cm.LOCATIONS_LOCKED, value)
            .where(cm.BOOK_ID.`in`(bookIds))
            .execute()
    }
}
