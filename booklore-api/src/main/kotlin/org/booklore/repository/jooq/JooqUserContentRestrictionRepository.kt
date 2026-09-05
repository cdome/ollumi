package org.booklore.repository.jooq

import org.booklore.jooq.tables.UserContentRestriction.USER_CONTENT_RESTRICTION
import org.booklore.model.dto.ContentRestriction
import org.booklore.model.enums.ContentRestrictionMode
import org.booklore.model.enums.ContentRestrictionType
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

/**
 * jOOQ replacement for the JPA UserContentRestrictionRepository. Returns the web
 * `ContentRestriction` DTO directly (it covers every column), so the old entity->DTO
 * mapper is gone. Enums are stored/read by name; created_at is stamped explicitly
 * (was @PrePersist). Reads carry user_id straight into the DTO, so the @ManyToOne
 * `user` navigation the old toDto relied on is no longer needed.
 */
@Repository
class JooqUserContentRestrictionRepository(private val dsl: DSLContext) {

    private val t = USER_CONTENT_RESTRICTION

    fun findByUserId(userId: Long): List<ContentRestriction> =
        dsl.select(t.ID, t.USER_ID, t.RESTRICTION_TYPE, t.MODE, t.VALUE, t.CREATED_AT)
            .from(t)
            .where(t.USER_ID.eq(userId))
            .orderBy(t.ID)
            .fetch { toDto(it) }

    fun findById(id: Long): Optional<ContentRestriction> =
        Optional.ofNullable(
            dsl.select(t.ID, t.USER_ID, t.RESTRICTION_TYPE, t.MODE, t.VALUE, t.CREATED_AT)
                .from(t)
                .where(t.ID.eq(id))
                .fetchOne()
                ?.let { toDto(it) }
        )

    fun existsById(id: Long): Boolean = dsl.fetchExists(t, t.ID.eq(id))

    fun existsByUserIdAndRestrictionTypeAndValue(userId: Long, type: ContentRestrictionType, value: String): Boolean =
        dsl.fetchExists(t, t.USER_ID.eq(userId).and(t.RESTRICTION_TYPE.eq(type.name)).and(t.VALUE.eq(value)))

    fun insert(userId: Long, type: ContentRestrictionType, mode: ContentRestrictionMode, value: String): ContentRestriction {
        val now = LocalDateTime.now()
        val id = dsl.insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.RESTRICTION_TYPE, type.name)
            .set(t.MODE, mode.name)
            .set(t.VALUE, value)
            .set(t.CREATED_AT, now)
            .returning(t.ID)
            .fetchOne()!!.id!!
        return ContentRestriction.builder()
            .id(id).userId(userId).restrictionType(type).mode(mode).value(value).createdAt(now).build()
    }

    fun insertAll(userId: Long, restrictions: List<ContentRestriction>): List<ContentRestriction> =
        restrictions.map { insert(userId, it.restrictionType, it.mode, it.value) }

    fun deleteByUserId(userId: Long): Int =
        dsl.deleteFrom(t).where(t.USER_ID.eq(userId)).execute()

    fun deleteById(id: Long): Int =
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()

    private fun toDto(r: Record): ContentRestriction =
        ContentRestriction.builder()
            .id(r.get(t.ID))
            .userId(r.get(t.USER_ID))
            .restrictionType(ContentRestrictionType.valueOf(r.get(t.RESTRICTION_TYPE)))
            .mode(ContentRestrictionMode.valueOf(r.get(t.MODE)))
            .value(r.get(t.VALUE))
            .createdAt(r.get(t.CREATED_AT))
            .build()
}
