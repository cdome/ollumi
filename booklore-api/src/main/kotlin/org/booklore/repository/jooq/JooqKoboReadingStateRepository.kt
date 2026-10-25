package org.booklore.repository.jooq

import org.booklore.jooq.tables.KoboReadingState.KOBO_READING_STATE
import org.booklore.jooq.tables.records.KoboReadingStateRecord
import org.booklore.model.dto.kobo.KoboReadingState
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper

/**
 * jOOQ access for `kobo_reading_state` (replaces the JPA entity + KoboReadingStateMapper).
 * Returns the KoboReadingState DTO directly: JSON columns are (de)serialized here and the
 * `last_modified_string` column maps to the DTO's `lastModified`. Uses a bare ObjectMapper to
 * match the former mapper's serialization behaviour exactly.
 */
@Repository
class JooqKoboReadingStateRepository(private val dsl: DSLContext) {

    private val t = KOBO_READING_STATE
    private val objectMapper = ObjectMapper()
    private val surroundingQuotes = Regex("^\"|\"$")

    fun findByEntitlementIdAndUserId(entitlementId: String, userId: Long): KoboReadingState? =
        dsl.selectFrom(t)
            .where(t.ENTITLEMENT_ID.eq(entitlementId).and(t.USER_ID.eq(userId)))
            .fetchOne()?.let(::toDto)

    fun findFirstByEntitlementIdWithNullUserOrderByPriority(entitlementId: String): KoboReadingState? =
        dsl.selectFrom(t)
            .where(t.ENTITLEMENT_ID.eq(entitlementId).and(t.USER_ID.isNull))
            .orderBy(t.PRIORITY_TIMESTAMP.desc(), t.LAST_MODIFIED_STRING.desc(), t.ID.desc())
            .limit(1)
            .fetchOne()?.let(::toDto)

    /** Insert a new row for the given user. The DTO fields are persisted verbatim (the caller has
     *  already cleaned/normalized them); entitlement_id is cleaned to match the old mapper. */
    fun insert(userId: Long, dto: KoboReadingState) {
        dsl.insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.ENTITLEMENT_ID, cleanString(dto.entitlementId))
            .set(t.CREATED, dto.created)
            .set(t.LAST_MODIFIED, dto.lastModified)
            .set(t.LAST_MODIFIED_STRING, dto.lastModified)
            .set(t.PRIORITY_TIMESTAMP, dto.priorityTimestamp)
            .set(t.CURRENT_BOOKMARK_JSON, toJson(dto.currentBookmark))
            .set(t.STATISTICS_JSON, toJson(dto.statistics))
            .set(t.STATUS_INFO_JSON, toJson(dto.statusInfo))
            .execute()
    }

    /** Update the mutable fields of an existing (entitlement, user) row (the merged JSON blobs +
     *  last_modified/priority timestamps). created/entitlement_id/user_id are left untouched. */
    fun updateByEntitlementIdAndUserId(entitlementId: String, userId: Long, dto: KoboReadingState) {
        dsl.update(t)
            .set(t.CURRENT_BOOKMARK_JSON, toJson(dto.currentBookmark))
            .set(t.STATISTICS_JSON, toJson(dto.statistics))
            .set(t.STATUS_INFO_JSON, toJson(dto.statusInfo))
            .set(t.LAST_MODIFIED_STRING, dto.lastModified)
            .set(t.LAST_MODIFIED, dto.lastModified)
            .set(t.PRIORITY_TIMESTAMP, dto.priorityTimestamp)
            .where(t.ENTITLEMENT_ID.eq(entitlementId).and(t.USER_ID.eq(userId)))
            .execute()
    }

    fun deleteByEntitlementIdAndUserId(entitlementId: String, userId: Long) {
        dsl.deleteFrom(t)
            .where(t.ENTITLEMENT_ID.eq(entitlementId).and(t.USER_ID.eq(userId)))
            .execute()
    }

    private fun toDto(r: KoboReadingStateRecord): KoboReadingState =
        KoboReadingState.builder()
            .entitlementId(cleanString(r.entitlementId))
            .created(r.created)
            .lastModified(r.lastModifiedString)
            .priorityTimestamp(r.priorityTimestamp)
            .currentBookmark(fromJson(r.currentBookmarkJson, KoboReadingState.CurrentBookmark::class.java))
            .statistics(fromJson(r.statisticsJson, KoboReadingState.Statistics::class.java))
            .statusInfo(fromJson(r.statusInfoJson, KoboReadingState.StatusInfo::class.java))
            .build()

    private fun cleanString(value: String?): String? =
        value?.let { surroundingQuotes.replace(it, "") }

    private fun toJson(value: Any?): String? =
        value?.let { objectMapper.writeValueAsString(it) }

    private fun <T> fromJson(json: String?, clazz: Class<T>): T? =
        json?.let { objectMapper.readValue(it, clazz) }
}
