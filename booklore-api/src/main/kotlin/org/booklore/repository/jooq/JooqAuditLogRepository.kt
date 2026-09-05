package org.booklore.repository.jooq

import org.booklore.jooq.tables.AuditLog.AUDIT_LOG
import org.booklore.model.dto.response.AuditLogDto
import org.booklore.model.enums.AuditAction
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqAuditLogRepository(private val dsl: DSLContext) {

    fun findAll(pageable: Pageable): Page<AuditLogDto> {
        val total = dsl.fetchCount(AUDIT_LOG)

        val content = dsl.selectFrom(AUDIT_LOG)
            .orderBy(AUDIT_LOG.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch(::toDto)

        return PageableHelper.toPage(content, total.toLong(), pageable)
    }

    fun findFiltered(
        action: AuditAction?,
        userId: Long?,
        username: String?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        pageable: Pageable
    ): Page<AuditLogDto> {
        var condition = DSL.noCondition()
        action?.let { condition = condition.and(AUDIT_LOG.ACTION.eq(it.name)) }
        userId?.let { condition = condition.and(AUDIT_LOG.USER_ID.eq(it)) }
        username?.let { condition = condition.and(AUDIT_LOG.USERNAME.eq(it)) }
        from?.let { condition = condition.and(AUDIT_LOG.CREATED_AT.ge(it)) }
        to?.let { condition = condition.and(AUDIT_LOG.CREATED_AT.le(it)) }

        val total = dsl.fetchCount(AUDIT_LOG, condition)

        val content = dsl.selectFrom(AUDIT_LOG)
            .where(condition)
            .orderBy(AUDIT_LOG.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch(::toDto)

        return PageableHelper.toPage(content, total.toLong(), pageable)
    }

    fun findDistinctUsernames(): List<String> =
        dsl.selectDistinct(AUDIT_LOG.USERNAME)
            .from(AUDIT_LOG)
            .orderBy(AUDIT_LOG.USERNAME)
            .fetch(AUDIT_LOG.USERNAME)

    fun insert(
        userId: Long?,
        username: String,
        action: AuditAction,
        entityType: String?,
        entityId: Long?,
        description: String?,
        ipAddress: String?,
        countryCode: String?,
    ) {
        dsl.insertInto(AUDIT_LOG)
            .set(AUDIT_LOG.USER_ID, userId)
            .set(AUDIT_LOG.USERNAME, username)
            .set(AUDIT_LOG.ACTION, action.name)
            .set(AUDIT_LOG.ENTITY_TYPE, entityType)
            .set(AUDIT_LOG.ENTITY_ID, entityId)
            .set(AUDIT_LOG.DESCRIPTION, description)
            .set(AUDIT_LOG.IP_ADDRESS, ipAddress)
            .set(AUDIT_LOG.COUNTRY_CODE, countryCode)
            .set(AUDIT_LOG.CREATED_AT, LocalDateTime.now())
            .execute()
    }

    private fun toDto(record: Record): AuditLogDto =
        AuditLogDto.builder()
            .id(record.get(AUDIT_LOG.ID))
            .userId(record.get(AUDIT_LOG.USER_ID))
            .username(record.get(AUDIT_LOG.USERNAME))
            .action(AuditAction.valueOf(record.get(AUDIT_LOG.ACTION)))
            .entityType(record.get(AUDIT_LOG.ENTITY_TYPE))
            .entityId(record.get(AUDIT_LOG.ENTITY_ID))
            .description(record.get(AUDIT_LOG.DESCRIPTION))
            .ipAddress(record.get(AUDIT_LOG.IP_ADDRESS))
            .countryCode(record.get(AUDIT_LOG.COUNTRY_CODE))
            .createdAt(record.get(AUDIT_LOG.CREATED_AT))
            .build()
}
