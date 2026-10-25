package org.booklore.repository.jooq

import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.records.LibraryRecord
import org.booklore.model.dto.Sort
import org.booklore.model.entity.LibraryEntity
import org.booklore.model.enums.BookFileType
import org.booklore.model.enums.IconType
import org.booklore.model.enums.LibraryOrganizationMode
import org.booklore.model.enums.MetadataSource
import org.booklore.model.enums.SortDirection
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * jOOQ access to the library table, replacing LibraryRepository. Library declares no relations at
 * all (a standalone leaf entity, confirmed by scoping: nothing else has a @ManyToOne/@OneToMany into
 * or out of it), so every method below is a single-table read/write with no attach-children step -
 * unlike Book/BookFile/Shelf, which all needed a parent/child graph.
 *
 * Note: JooqBookEntityRepository has its own independent copy of this same LIBRARY row mapping (for
 * BookEntity.library), kept separate deliberately rather than having one repo depend on the other -
 * that mapping is already tested via the Book read model's golden-master tests, and touching it here
 * would risk an unrelated regression for a few lines of duplicated parsing logic.
 */
@Repository
class JooqLibraryRepository(private val dsl: DSLContext, private val objectMapper: ObjectMapper) {

    private val formatPriorityType = object : TypeReference<List<BookFileType>>() {}

    private fun toSort(raw: String?): Sort? {
        if (raw.isNullOrEmpty()) return null
        val parts = raw.split(",")
        val sort = Sort()
        sort.field = parts[0]
        sort.direction = SortDirection.valueOf(parts[1])
        return sort
    }

    private fun toEntity(r: LibraryRecord): LibraryEntity =
        LibraryEntity.builder()
            .id(r.id)
            .name(r.name)
            .sort(toSort(r.sort))
            .watch(r.watch == 1.toByte())
            .icon(r.icon)
            .iconType(r.iconType?.let { IconType.valueOf(it) })
            .fileNamingPattern(r.fileNamingPattern)
            .formatPriority(r.formatPriority?.let { objectMapper.readValue(it, formatPriorityType) } ?: ArrayList())
            .allowedFormats(r.allowedFormats?.let { objectMapper.readValue(it, formatPriorityType) })
            .organizationMode(r.organizationMode?.let { LibraryOrganizationMode.valueOf(it) })
            .metadataSource(r.metadataSource?.let { MetadataSource.valueOf(it) })
            .build()

    fun findById(id: Long): LibraryEntity? =
        dsl.selectFrom(LIBRARY).where(LIBRARY.ID.eq(id)).fetchOne()?.let(::toEntity)

    fun findAll(): List<LibraryEntity> =
        dsl.selectFrom(LIBRARY).orderBy(LIBRARY.ID).fetch().map(::toEntity)

    fun findByIdIn(ids: Collection<Long>): List<LibraryEntity> {
        if (ids.isEmpty()) return emptyList()
        return dsl.selectFrom(LIBRARY).where(LIBRARY.ID.`in`(ids)).fetch().map(::toEntity)
    }

    /** Mirrors the inherited JpaRepository.findAllById: same query as findByIdIn, kept as a separate
     * name for 1:1 traceability with its call site (BookLoreUserTransformer). */
    fun findAllById(ids: Collection<Long>): List<LibraryEntity> = findByIdIn(ids)

    fun count(): Long = dsl.fetchCount(LIBRARY).toLong()

    fun deleteById(id: Long) {
        dsl.deleteFrom(LIBRARY).where(LIBRARY.ID.eq(id)).execute()
    }

    /** Mirrors LibraryRepository.save: insert-or-update based on whether [library] already carries an
     * id, matching JPA save() semantics. Returns the same object, id populated on insert. */
    fun save(library: LibraryEntity): LibraryEntity {
        val sortStr = library.sort?.let { "${it.field},${it.direction.name}" }
        val formatPriorityJson = library.formatPriority?.takeIf { it.isNotEmpty() }?.let { objectMapper.writeValueAsString(it) }
        val allowedFormatsJson = library.allowedFormats?.takeIf { it.isNotEmpty() }?.let { objectMapper.writeValueAsString(it) }
        val existingId = library.id
        if (existingId == null) {
            val newId = dsl.insertInto(LIBRARY)
                .set(LIBRARY.NAME, library.name)
                .set(LIBRARY.SORT, sortStr)
                .set(LIBRARY.WATCH, if (library.isWatch) 1.toByte() else 0.toByte())
                .set(LIBRARY.ICON, library.icon)
                .set(LIBRARY.ICON_TYPE, library.iconType?.name)
                .set(LIBRARY.FILE_NAMING_PATTERN, library.fileNamingPattern)
                .set(LIBRARY.FORMAT_PRIORITY, formatPriorityJson)
                .set(LIBRARY.ALLOWED_FORMATS, allowedFormatsJson)
                .set(LIBRARY.ORGANIZATION_MODE, library.organizationMode?.name)
                .set(LIBRARY.METADATA_SOURCE, library.metadataSource?.name)
                .returning(LIBRARY.ID)
                .fetchOne()!!.id!!
            library.id = newId
        } else {
            dsl.update(LIBRARY)
                .set(LIBRARY.NAME, library.name)
                .set(LIBRARY.SORT, sortStr)
                .set(LIBRARY.WATCH, if (library.isWatch) 1.toByte() else 0.toByte())
                .set(LIBRARY.ICON, library.icon)
                .set(LIBRARY.ICON_TYPE, library.iconType?.name)
                .set(LIBRARY.FILE_NAMING_PATTERN, library.fileNamingPattern)
                .set(LIBRARY.FORMAT_PRIORITY, formatPriorityJson)
                .set(LIBRARY.ALLOWED_FORMATS, allowedFormatsJson)
                .set(LIBRARY.ORGANIZATION_MODE, library.organizationMode?.name)
                .set(LIBRARY.METADATA_SOURCE, library.metadataSource?.name)
                .where(LIBRARY.ID.eq(existingId))
                .execute()
        }
        return library
    }
}
