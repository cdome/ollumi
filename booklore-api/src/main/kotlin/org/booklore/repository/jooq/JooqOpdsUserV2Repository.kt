package org.booklore.repository.jooq

import org.booklore.jooq.tables.OpdsUserV2.OPDS_USER_V2
import org.booklore.jooq.tables.records.OpdsUserV2Record
import org.booklore.model.dto.OpdsUserV2
import org.booklore.model.enums.OpdsSortOrder
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

@Repository
class JooqOpdsUserV2Repository(private val dsl: DSLContext) {

    private val t = OPDS_USER_V2

    fun findByUsername(username: String): Optional<OpdsUserV2> =
        Optional.ofNullable(dsl.selectFrom(t).where(t.USERNAME.eq(username)).fetchOne()?.let(::toDto))

    fun findById(id: Long): Optional<OpdsUserV2> =
        Optional.ofNullable(dsl.selectFrom(t).where(t.ID.eq(id)).fetchOne()?.let(::toDto))

    fun findByUserId(userId: Long): List<OpdsUserV2> =
        dsl.selectFrom(t).where(t.USER_ID.eq(userId)).fetch().map(::toDto)

    fun count(): Long = dsl.fetchCount(t).toLong()

    fun insert(userId: Long, username: String, passwordHash: String, sortOrder: OpdsSortOrder): OpdsUserV2 {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val id = dsl.insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.USERNAME, username)
            .set(t.PASSWORD_HASH, passwordHash)
            .set(t.SORT_ORDER, sortOrder.name)
            .set(t.CREATED_AT, now)
            .set(t.UPDATED_AT, now)
            .returning(t.ID)
            .fetchOne()!!.id
        return requireById(id)
    }

    fun updateSortOrder(id: Long, sortOrder: OpdsSortOrder): OpdsUserV2 {
        dsl.update(t)
            .set(t.SORT_ORDER, sortOrder.name)
            .set(t.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
            .where(t.ID.eq(id))
            .execute()
        return requireById(id)
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    private fun requireById(id: Long): OpdsUserV2 =
        toDto(dsl.selectFrom(t).where(t.ID.eq(id)).fetchOne()
            ?: error("OPDS user not found: $id"))

    private fun toDto(r: OpdsUserV2Record): OpdsUserV2 = OpdsUserV2.builder()
        .id(r.id)
        .userId(r.userId)
        .username(r.username)
        .passwordHash(r.passwordHash)
        .sortOrder(OpdsSortOrder.valueOf(r.sortOrder))
        .build()
}
