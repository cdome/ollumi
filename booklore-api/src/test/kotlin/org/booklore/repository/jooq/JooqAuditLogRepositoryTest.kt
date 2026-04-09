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
}
