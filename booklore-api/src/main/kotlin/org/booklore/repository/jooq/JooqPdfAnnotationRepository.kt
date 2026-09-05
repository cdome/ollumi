package org.booklore.repository.jooq

import org.booklore.jooq.tables.PdfAnnotations.PDF_ANNOTATIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqPdfAnnotationRepository(private val dsl: DSLContext) {

    private val t = PDF_ANNOTATIONS

    fun findDataByBookIdAndUserId(bookId: Long, userId: Long): String? =
        dsl.select(t.DATA).from(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .fetchOne(t.DATA)

    /**
     * Insert-or-update the single (user, book) annotation blob. Replicates the former JPA
     * entity's @Version bump and @CreationTimestamp/@UpdateTimestamp behaviour explicitly.
     */
    fun upsert(bookId: Long, userId: Long, data: String) {
        val now = LocalDateTime.now()
        val existing = dsl.select(t.ID, t.VERSION).from(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .fetchOne()
        if (existing != null) {
            dsl.update(t)
                .set(t.DATA, data)
                .set(t.VERSION, (existing.get(t.VERSION) ?: 0L) + 1L)
                .set(t.UPDATED_AT, now)
                .where(t.ID.eq(existing.get(t.ID)))
                .execute()
        } else {
            dsl.insertInto(t)
                .set(t.BOOK_ID, bookId)
                .set(t.USER_ID, userId)
                .set(t.DATA, data)
                .set(t.VERSION, 0L)
                .set(t.CREATED_AT, now)
                .set(t.UPDATED_AT, now)
                .execute()
        }
    }

    fun deleteByBookIdAndUserId(bookId: Long, userId: Long) {
        dsl.deleteFrom(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .execute()
    }
}
