package org.booklore.repository.jooq

import org.booklore.jooq.tables.MagicShelf.MAGIC_SHELF
import org.booklore.jooq.tables.records.MagicShelfRecord
import org.booklore.model.enums.IconType
import org.booklore.repository.jooq.dto.MagicShelfRow
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
class JooqMagicShelfRepository(private val dsl: DSLContext) {

    private val t = MAGIC_SHELF

    fun findAllByUserId(userId: Long): List<MagicShelfRow> =
        dsl.selectFrom(t).where(t.USER_ID.eq(userId)).fetch().map(::toRow)

    fun findAllPublic(): List<MagicShelfRow> =
        dsl.selectFrom(t).where(t.IS_PUBLIC.eq(1.toByte())).fetch().map(::toRow)

    fun findById(id: Long): Optional<MagicShelfRow> =
        Optional.ofNullable(dsl.selectFrom(t).where(t.ID.eq(id)).fetchOne()?.let(::toRow))

    fun existsByUserIdAndName(userId: Long, name: String): Boolean =
        dsl.fetchExists(dsl.selectFrom(t).where(t.USER_ID.eq(userId).and(t.NAME.eq(name))))

    fun count(): Long = dsl.fetchCount(t).toLong()

    fun insert(
        userId: Long,
        name: String,
        icon: String?,
        iconType: IconType?,
        filterJson: String,
        isPublic: Boolean,
    ): MagicShelfRow {
        val now = LocalDateTime.now()
        val id = dsl.insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.NAME, name)
            .set(t.ICON, icon)
            .set(t.ICON_TYPE, iconType?.name)
            .set(t.FILTER_JSON, filterJson)
            .set(t.IS_PUBLIC, isPublic.toByte())
            .set(t.CREATED_AT, now)
            .set(t.UPDATED_AT, now)
            .returning(t.ID)
            .fetchOne()!!.id
        return MagicShelfRow(id, userId, name, icon, iconType, filterJson, isPublic, now, now)
    }

    fun update(
        id: Long,
        userId: Long,
        name: String,
        icon: String?,
        iconType: IconType?,
        filterJson: String,
        isPublic: Boolean,
    ): MagicShelfRow {
        val now = LocalDateTime.now()
        dsl.update(t)
            .set(t.NAME, name)
            .set(t.ICON, icon)
            .set(t.ICON_TYPE, iconType?.name)
            .set(t.FILTER_JSON, filterJson)
            .set(t.IS_PUBLIC, isPublic.toByte())
            .set(t.UPDATED_AT, now)
            .where(t.ID.eq(id))
            .execute()
        return MagicShelfRow(id, userId, name, icon, iconType, filterJson, isPublic, now, now)
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    private fun toRow(r: MagicShelfRecord): MagicShelfRow =
        MagicShelfRow(
            id = r.id,
            userId = r.userId,
            name = r.name,
            icon = r.get(t.ICON),
            iconType = r.get(t.ICON_TYPE)?.let { IconType.valueOf(it) },
            filterJson = r.filterJson,
            isPublic = (r.get(t.IS_PUBLIC) ?: 0).toInt() != 0,
            createdAt = r.get(t.CREATED_AT),
            updatedAt = r.get(t.UPDATED_AT),
        )

    private fun Boolean.toByte(): Byte = if (this) 1 else 0
}
