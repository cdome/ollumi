package org.booklore.repository.jooq

import org.booklore.jooq.tables.KoreaderUser.KOREADER_USER
import org.booklore.jooq.tables.records.KoreaderUserRecord
import org.booklore.repository.jooq.dto.KoreaderUserRow
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

@Repository
class JooqKoreaderUserRepository(private val dsl: DSLContext) {

    private val t = KOREADER_USER

    fun findByUsername(username: String): Optional<KoreaderUserRow> =
        Optional.ofNullable(dsl.selectFrom(t).where(t.USERNAME.eq(username)).fetchOne()?.let(::toRow))

    fun findByBookLoreUserId(bookLoreUserId: Long): Optional<KoreaderUserRow> =
        Optional.ofNullable(dsl.selectFrom(t).where(t.BOOKLORE_USER_ID.eq(bookLoreUserId)).fetchOne()?.let(::toRow))

    fun count(): Long = dsl.fetchCount(t).toLong()

    /** Upsert: UPDATE by id when present (bumps updated_at), otherwise INSERT (stamps created_at + updated_at). */
    fun save(row: KoreaderUserRow): KoreaderUserRow {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val id = row.id
        if (id != null) {
            dsl.update(t)
                .set(t.USERNAME, row.username)
                .set(t.PASSWORD, row.password)
                .set(t.PASSWORD_MD5, row.passwordMD5)
                .set(t.SYNC_ENABLED, row.syncEnabled.toByte())
                .set(t.SYNC_WITH_BOOKLORE_READER, row.syncWithBookloreReader.toByte())
                .set(t.BOOKLORE_USER_ID, row.bookLoreUserId)
                .set(t.UPDATED_AT, now)
                .where(t.ID.eq(id))
                .execute()
            row.updatedAt = now.toInstant()
        } else {
            row.id = dsl.insertInto(t)
                .set(t.USERNAME, row.username)
                .set(t.PASSWORD, row.password)
                .set(t.PASSWORD_MD5, row.passwordMD5)
                .set(t.SYNC_ENABLED, row.syncEnabled.toByte())
                .set(t.SYNC_WITH_BOOKLORE_READER, row.syncWithBookloreReader.toByte())
                .set(t.BOOKLORE_USER_ID, row.bookLoreUserId)
                .set(t.CREATED_AT, now)
                .set(t.UPDATED_AT, now)
                .returning(t.ID)
                .fetchOne()!!.id
            row.createdAt = now.toInstant()
            row.updatedAt = now.toInstant()
        }
        return row
    }

    private fun toRow(r: KoreaderUserRecord) = KoreaderUserRow(
        id = r.get(t.ID),
        username = r.get(t.USERNAME),
        password = r.get(t.PASSWORD),
        passwordMD5 = r.get(t.PASSWORD_MD5),
        syncEnabled = r.get(t.SYNC_ENABLED) == 1.toByte(),
        syncWithBookloreReader = r.get(t.SYNC_WITH_BOOKLORE_READER) == 1.toByte(),
        bookLoreUserId = r.get(t.BOOKLORE_USER_ID),
        createdAt = r.get(t.CREATED_AT)?.toInstant(),
        updatedAt = r.get(t.UPDATED_AT)?.toInstant(),
    )

    private fun Boolean.toByte(): Byte = if (this) 1 else 0
    private fun LocalDateTime.toInstant(): Instant = this.toInstant(ZoneOffset.UTC)
}
