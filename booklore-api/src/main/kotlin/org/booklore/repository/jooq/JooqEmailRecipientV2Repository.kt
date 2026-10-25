package org.booklore.repository.jooq

import org.booklore.jooq.tables.EmailRecipientV2.EMAIL_RECIPIENT_V2
import org.booklore.jooq.tables.records.EmailRecipientV2Record
import org.booklore.model.dto.EmailRecipientV2
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqEmailRecipientV2Repository(private val dsl: DSLContext) {

    private val t = EMAIL_RECIPIENT_V2

    fun findByIdAndUserId(id: Long, userId: Long): EmailRecipientV2? =
        dsl.selectFrom(t).where(t.ID.eq(id).and(t.USER_ID.eq(userId))).fetchOne()?.let(::toDto)

    fun findAllByUserId(userId: Long): List<EmailRecipientV2> =
        dsl.selectFrom(t).where(t.USER_ID.eq(userId)).orderBy(t.ID).fetch().map(::toDto)

    fun findAll(): List<EmailRecipientV2> =
        dsl.selectFrom(t).orderBy(t.ID).fetch().map(::toDto)

    fun findDefaultEmailRecipientByUserId(userId: Long): EmailRecipientV2? =
        dsl.selectFrom(t).where(t.IS_DEFAULT.eq(1.toByte()).and(t.USER_ID.eq(userId))).fetchOne()?.let(::toDto)

    fun count(): Long = dsl.fetchCount(t).toLong()

    fun updateAllRecipientsToNonDefault(userId: Long) {
        dsl.update(t).set(t.IS_DEFAULT, 0.toByte())
            .where(t.IS_DEFAULT.eq(1.toByte()).and(t.USER_ID.eq(userId)))
            .execute()
    }

    fun insert(userId: Long, email: String, name: String, defaultRecipient: Boolean): EmailRecipientV2 {
        val id = dsl.insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.EMAIL, email)
            .set(t.NAME, name)
            .set(t.IS_DEFAULT, if (defaultRecipient) 1.toByte() else 0.toByte())
            .returning(t.ID)
            .fetchOne()!!
            .id
        return findById(id)!!
    }

    fun update(id: Long, email: String, name: String, defaultRecipient: Boolean): EmailRecipientV2 {
        dsl.update(t)
            .set(t.EMAIL, email)
            .set(t.NAME, name)
            .set(t.IS_DEFAULT, if (defaultRecipient) 1.toByte() else 0.toByte())
            .where(t.ID.eq(id))
            .execute()
        return findById(id)!!
    }

    fun markDefaultById(id: Long) {
        dsl.update(t).set(t.IS_DEFAULT, 1.toByte()).where(t.ID.eq(id)).execute()
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    private fun findById(id: Long): EmailRecipientV2? =
        dsl.selectFrom(t).where(t.ID.eq(id)).fetchOne()?.let(::toDto)

    private fun toDto(r: EmailRecipientV2Record): EmailRecipientV2 =
        EmailRecipientV2.builder()
            .id(r.id)
            .userId(r.userId)
            .email(r.email)
            .name(r.name)
            .defaultRecipient(r.get(t.IS_DEFAULT).let { it != null && it.toInt() != 0 })
            .build()
}
