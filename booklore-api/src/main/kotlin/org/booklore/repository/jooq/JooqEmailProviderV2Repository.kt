package org.booklore.repository.jooq

import org.booklore.jooq.tables.EmailProviderV2.EMAIL_PROVIDER_V2
import org.booklore.jooq.tables.UserPermissions.USER_PERMISSIONS
import org.booklore.jooq.tables.records.EmailProviderV2Record
import org.booklore.repository.jooq.dto.EmailProviderV2Row
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqEmailProviderV2Repository(private val dsl: DSLContext) {

    private val t = EMAIL_PROVIDER_V2

    /** user_ids of admin users — the JPQL subquery `u.permissions.permissionAdmin = true`. */
    private fun adminUserIds() =
        dsl.select(USER_PERMISSIONS.USER_ID).from(USER_PERMISSIONS).where(USER_PERMISSIONS.PERMISSION_ADMIN.eq(1.toByte()))

    fun findByIdAndUserId(id: Long, userId: Long): EmailProviderV2Row? =
        dsl.selectFrom(t).where(t.ID.eq(id).and(t.USER_ID.eq(userId))).fetchOne()?.let(::toRow)

    fun findAllByUserId(userId: Long): List<EmailProviderV2Row> =
        dsl.selectFrom(t).where(t.USER_ID.eq(userId)).orderBy(t.ID).fetch().map(::toRow)

    fun findAllBySharedTrueAndAdmin(): List<EmailProviderV2Row> =
        dsl.selectFrom(t).where(t.SHARED.eq(1.toByte()).and(t.USER_ID.`in`(adminUserIds())))
            .orderBy(t.ID).fetch().map(::toRow)

    fun findSharedProviderById(id: Long): EmailProviderV2Row? =
        dsl.selectFrom(t).where(t.ID.eq(id).and(t.SHARED.eq(1.toByte())).and(t.USER_ID.`in`(adminUserIds())))
            .fetchOne()?.let(::toRow)

    fun findAccessibleProvider(id: Long, userId: Long): EmailProviderV2Row? {
        val accessible: Condition = t.USER_ID.eq(userId)
            .or(t.SHARED.eq(1.toByte()).and(t.USER_ID.`in`(adminUserIds())))
        return dsl.selectFrom(t).where(t.ID.eq(id).and(accessible)).fetchOne()?.let(::toRow)
    }

    fun count(): Long = dsl.fetchCount(t).toLong()

    fun insert(row: EmailProviderV2Row): EmailProviderV2Row {
        val id = dsl.insertInto(t)
            .set(t.USER_ID, row.userId)
            .set(t.NAME, row.name)
            .set(t.HOST, row.host)
            .set(t.PORT, row.port)
            .set(t.USERNAME, row.username)
            .set(t.PASSWORD, row.password)
            .set(t.FROM_ADDRESS, row.fromAddress)
            .set(t.AUTH, row.auth.toByteFlag())
            .set(t.START_TLS, row.startTls.toByteFlag())
            .set(t.IS_DEFAULT, row.defaultProvider.toByteFlag())
            .set(t.SHARED, row.shared.toByteFlag())
            .returning(t.ID)
            .fetchOne()!!
            .id
        return findById(id)!!
    }

    fun update(row: EmailProviderV2Row): EmailProviderV2Row {
        dsl.update(t)
            .set(t.NAME, row.name)
            .set(t.HOST, row.host)
            .set(t.PORT, row.port)
            .set(t.USERNAME, row.username)
            .set(t.PASSWORD, row.password)
            .set(t.FROM_ADDRESS, row.fromAddress)
            .set(t.AUTH, row.auth.toByteFlag())
            .set(t.START_TLS, row.startTls.toByteFlag())
            .set(t.IS_DEFAULT, row.defaultProvider.toByteFlag())
            .set(t.SHARED, row.shared.toByteFlag())
            .where(t.ID.eq(row.id))
            .execute()
        return findById(row.id)!!
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    private fun findById(id: Long): EmailProviderV2Row? =
        dsl.selectFrom(t).where(t.ID.eq(id)).fetchOne()?.let(::toRow)

    private fun toRow(r: EmailProviderV2Record): EmailProviderV2Row =
        EmailProviderV2Row(
            id = r.id,
            userId = r.userId,
            name = r.name,
            host = r.host,
            port = r.port,
            username = r.username,
            password = r.password,
            fromAddress = r.fromAddress,
            auth = r.auth.toBoolFlag(),
            startTls = r.startTls.toBoolFlag(),
            defaultProvider = r.get(t.IS_DEFAULT).toBoolFlag(),
            shared = r.shared.toBoolFlag(),
        )

    private fun Boolean.toByteFlag(): Byte = if (this) 1 else 0
    private fun Byte?.toBoolFlag(): Boolean = this != null && this.toInt() != 0
}
