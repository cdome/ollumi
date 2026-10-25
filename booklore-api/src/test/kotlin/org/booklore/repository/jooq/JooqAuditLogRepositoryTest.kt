package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.AuditLog.AUDIT_LOG
import org.booklore.model.enums.AuditAction
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime

class JooqAuditLogRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repository: JooqAuditLogRepository

    @Autowired
    private lateinit var dsl: DSLContext

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(AUDIT_LOG).execute()

        dsl.insertInto(AUDIT_LOG)
            .set(AUDIT_LOG.USER_ID, 1L)
            .set(AUDIT_LOG.USERNAME, "admin")
            .set(AUDIT_LOG.ACTION, AuditAction.LOGIN_SUCCESS.name)
            .set(AUDIT_LOG.DESCRIPTION, "Admin logged in")
            .set(AUDIT_LOG.CREATED_AT, LocalDateTime.of(2026, 1, 1, 10, 0))
            .execute()

        dsl.insertInto(AUDIT_LOG)
            .set(AUDIT_LOG.USER_ID, 2L)
            .set(AUDIT_LOG.USERNAME, "reader")
            .set(AUDIT_LOG.ACTION, AuditAction.BOOK_UPLOADED.name)
            .set(AUDIT_LOG.DESCRIPTION, "Book uploaded")
            .set(AUDIT_LOG.CREATED_AT, LocalDateTime.of(2026, 1, 2, 14, 30))
            .execute()

        dsl.insertInto(AUDIT_LOG)
            .set(AUDIT_LOG.USER_ID, 1L)
            .set(AUDIT_LOG.USERNAME, "admin")
            .set(AUDIT_LOG.ACTION, AuditAction.SETTINGS_UPDATED.name)
            .set(AUDIT_LOG.DESCRIPTION, "Settings changed")
            .set(AUDIT_LOG.CREATED_AT, LocalDateTime.of(2026, 1, 3, 9, 0))
            .execute()
    }

    @Test
    fun `findAll returns paginated results`() {
        val page = repository.findAll(PageRequest.of(0, 2))

        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).hasSize(2)
        assertThat(page.content[0].action).isEqualTo(AuditAction.SETTINGS_UPDATED)
        assertThat(page.content[1].action).isEqualTo(AuditAction.BOOK_UPLOADED)
    }

    @Test
    fun `findAll second page`() {
        val page = repository.findAll(PageRequest.of(1, 2))

        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).hasSize(1)
        assertThat(page.content[0].action).isEqualTo(AuditAction.LOGIN_SUCCESS)
    }

    @Test
    fun `findFiltered by action`() {
        val page = repository.findFiltered(
            AuditAction.LOGIN_SUCCESS, null, null, null, null, PageRequest.of(0, 10)
        )

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content[0].username).isEqualTo("admin")
    }

    @Test
    fun `findFiltered by username`() {
        val page = repository.findFiltered(
            null, null, "admin", null, null, PageRequest.of(0, 10)
        )

        assertThat(page.totalElements).isEqualTo(2)
    }

    @Test
    fun `findFiltered by date range`() {
        val page = repository.findFiltered(
            null, null, null,
            LocalDateTime.of(2026, 1, 2, 0, 0),
            LocalDateTime.of(2026, 1, 2, 23, 59),
            PageRequest.of(0, 10)
        )

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content[0].action).isEqualTo(AuditAction.BOOK_UPLOADED)
    }

    @Test
    fun `findFiltered all nulls returns all`() {
        val page = repository.findFiltered(
            null, null, null, null, null, PageRequest.of(0, 10)
        )

        assertThat(page.totalElements).isEqualTo(3)
    }

    @Test
    fun `findDistinctUsernames returns sorted usernames`() {
        val usernames = repository.findDistinctUsernames()

        assertThat(usernames).containsExactly("admin", "reader")
    }

    @Test
    fun `insert persists all fields and defaults createdAt`() {
        repository.insert(
            userId = 7L,
            username = "writer",
            action = AuditAction.METADATA_UPDATED,
            entityType = "BOOK",
            entityId = 42L,
            description = "Edited metadata",
            ipAddress = "10.0.0.1",
            countryCode = "US",
        )

        val row = dsl.selectFrom(AUDIT_LOG).where(AUDIT_LOG.USERNAME.eq("writer")).fetchOne()!!
        assertThat(row.userId).isEqualTo(7L)
        assertThat(row.action).isEqualTo(AuditAction.METADATA_UPDATED.name)
        assertThat(row.entityType).isEqualTo("BOOK")
        assertThat(row.entityId).isEqualTo(42L)
        assertThat(row.description).isEqualTo("Edited metadata")
        assertThat(row.ipAddress).isEqualTo("10.0.0.1")
        assertThat(row.countryCode).isEqualTo("US")
        assertThat(row.createdAt).isNotNull()
    }

    @Test
    fun `insert allows null optional fields`() {
        repository.insert(
            userId = null,
            username = "system",
            action = AuditAction.LOGIN_SUCCESS,
            entityType = null,
            entityId = null,
            description = "System event",
            ipAddress = null,
            countryCode = null,
        )

        val row = dsl.selectFrom(AUDIT_LOG).where(AUDIT_LOG.USERNAME.eq("system")).fetchOne()!!
        assertThat(row.get(AUDIT_LOG.USER_ID) as Long?).isNull()
        assertThat(row.get(AUDIT_LOG.ENTITY_TYPE) as String?).isNull()
        assertThat(row.get(AUDIT_LOG.IP_ADDRESS) as String?).isNull()
        assertThat(row.get(AUDIT_LOG.COUNTRY_CODE) as String?).isNull()
    }
}
