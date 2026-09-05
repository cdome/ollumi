package org.booklore.repository.jooq

import org.booklore.jooq.tables.OidcGroupMapping.OIDC_GROUP_MAPPING
import org.booklore.jooq.tables.records.OidcGroupMappingRecord
import org.booklore.model.dto.OidcGroupMapping
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@Repository
class JooqOidcGroupMappingRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) {

    private val t = OIDC_GROUP_MAPPING
    private val stringListType = object : TypeReference<List<String>>() {}
    private val longListType = object : TypeReference<List<Long>>() {}

    fun findAll(): List<OidcGroupMapping> =
        dsl.selectFrom(t).orderBy(t.ID).fetch().map(::toDto)

    fun findById(id: Long): OidcGroupMapping? =
        dsl.selectFrom(t).where(t.ID.eq(id)).fetchOne()?.let(::toDto)

    fun findByOidcGroupClaimIn(claims: Collection<String>): List<OidcGroupMapping> =
        dsl.selectFrom(t).where(t.OIDC_GROUP_CLAIM.`in`(claims)).fetch().map(::toDto)

    fun insert(dto: OidcGroupMapping): OidcGroupMapping {
        val now = LocalDateTime.now()
        val id = dsl.insertInto(t)
            .set(t.OIDC_GROUP_CLAIM, dto.oidcGroupClaim())
            .set(t.IS_ADMIN, dto.isAdmin.toByteFlag())
            .set(t.PERMISSIONS, toJson(dto.permissions()))
            .set(t.LIBRARY_IDS, toJson(dto.libraryIds()))
            .set(t.DESCRIPTION, dto.description())
            .set(t.CREATED_AT, now)
            .set(t.UPDATED_AT, now)
            .returning(t.ID)
            .fetchOne()!!
            .id
        return findById(id)!!
    }

    fun update(id: Long, dto: OidcGroupMapping): OidcGroupMapping {
        dsl.update(t)
            .set(t.OIDC_GROUP_CLAIM, dto.oidcGroupClaim())
            .set(t.IS_ADMIN, dto.isAdmin.toByteFlag())
            .set(t.PERMISSIONS, toJson(dto.permissions()))
            .set(t.LIBRARY_IDS, toJson(dto.libraryIds()))
            .set(t.DESCRIPTION, dto.description())
            .set(t.UPDATED_AT, LocalDateTime.now())
            .where(t.ID.eq(id))
            .execute()
        return findById(id)!!
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    private fun toDto(r: OidcGroupMappingRecord): OidcGroupMapping =
        OidcGroupMapping(
            r.id,
            r.oidcGroupClaim,
            r.isAdmin.toInt() != 0,
            parseStringList(r.permissions),
            parseLongList(r.libraryIds),
            r.description,
        )

    private fun toJson(list: List<*>?): String =
        if (list.isNullOrEmpty()) "[]" else objectMapper.writeValueAsString(list)

    private fun parseStringList(json: String?): List<String> =
        if (json.isNullOrBlank()) emptyList() else runCatching { objectMapper.readValue(json, stringListType) }.getOrDefault(emptyList())

    private fun parseLongList(json: String?): List<Long> =
        if (json.isNullOrBlank()) emptyList() else runCatching { objectMapper.readValue(json, longListType) }.getOrDefault(emptyList())

    private fun Boolean.toByteFlag(): Byte = if (this) 1 else 0
}
