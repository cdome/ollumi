package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.PdfAnnotations.PDF_ANNOTATIONS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class JooqPdfAnnotationRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqPdfAnnotationRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var bookId: Long = 0

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(PDF_ANNOTATIONS).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY).execute()
        dsl.deleteFrom(USERS).execute()

        userId = dsl.insertInto(USERS)
            .set(USERS.USERNAME, "reader")
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, "reader")
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
        val libId = dsl.insertInto(LIBRARY)
            .set(LIBRARY.NAME, "Library")
            .returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!
        bookId = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libId)
            .set(BOOK.ADDED_ON, LocalDateTime.now())
            .returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
    }

    @Test
    fun `find returns null when absent`() {
        assertThat(repository.findDataByBookIdAndUserId(bookId, userId)).isNull()
    }

    @Test
    fun `upsert inserts with version zero and timestamps`() {
        repository.upsert(bookId, userId, "{\"a\":1}")

        val row = dsl.selectFrom(PDF_ANNOTATIONS).fetchOne()!!
        assertThat(row.data).isEqualTo("{\"a\":1}")
        assertThat(row.version).isEqualTo(0L)
        assertThat(row.createdAt).isNotNull()
        assertThat(row.updatedAt).isNotNull()
        assertThat(repository.findDataByBookIdAndUserId(bookId, userId)).isEqualTo("{\"a\":1}")
    }

    @Test
    fun `upsert updates existing row, bumps version, keeps single row`() {
        repository.upsert(bookId, userId, "first")
        val createdAt = dsl.selectFrom(PDF_ANNOTATIONS).fetchOne()!!.createdAt

        repository.upsert(bookId, userId, "second")

        val row = dsl.selectFrom(PDF_ANNOTATIONS).fetchOne()!!
        assertThat(dsl.fetchCount(PDF_ANNOTATIONS)).isEqualTo(1)
        assertThat(row.data).isEqualTo("second")
        assertThat(row.version).isEqualTo(1L)
        assertThat(row.createdAt).isEqualTo(createdAt)
    }

    @Test
    fun `delete removes only the matching user-book row`() {
        repository.upsert(bookId, userId, "keep-or-drop")
        repository.deleteByBookIdAndUserId(bookId, userId)
        assertThat(repository.findDataByBookIdAndUserId(bookId, userId)).isNull()
    }
}
