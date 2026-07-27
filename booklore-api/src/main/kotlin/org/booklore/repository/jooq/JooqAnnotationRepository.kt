package org.booklore.repository.jooq

import org.booklore.jooq.tables.Annotations.ANNOTATIONS
import org.booklore.jooq.tables.records.AnnotationsRecord
import org.booklore.model.dto.Annotation
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqAnnotationRepository(private val dsl: DSLContext) {

    private val t = ANNOTATIONS

    fun findByBookIdAndUserIdOrderByCreatedAtDesc(bookId: Long, userId: Long): List<Annotation> =
        dsl.selectFrom(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .orderBy(t.CREATED_AT.desc())
            .fetch()
            .map(::toDto)

    fun findByIdAndUserId(id: Long, userId: Long): Annotation? =
        dsl.selectFrom(t)
            .where(t.ID.eq(id).and(t.USER_ID.eq(userId)))
            .fetchOne()?.let(::toDto)

    fun existsByCfiAndBookIdAndUserId(cfi: String, bookId: Long, userId: Long): Boolean =
        dsl.fetchExists(t, t.CFI.eq(cfi).and(t.BOOK_ID.eq(bookId)).and(t.USER_ID.eq(userId)))

    fun insert(
        bookId: Long,
        userId: Long,
        cfi: String,
        text: String,
        color: String,
        style: String,
        note: String?,
        chapterTitle: String?,
    ): Annotation {
        val now = LocalDateTime.now()
        val id = dsl.insertInto(t)
            .set(t.BOOK_ID, bookId)
            .set(t.USER_ID, userId)
            .set(t.CFI, cfi)
            .set(t.TEXT, text)
            .set(t.COLOR, color)
            .set(t.STYLE, style)
            .set(t.NOTE, note)
            .set(t.CHAPTER_TITLE, chapterTitle)
            .set(t.VERSION, 0L)
            .set(t.CREATED_AT, now)
            .set(t.UPDATED_AT, now)
            .returning(t.ID)
            .fetchOne()!!
            .id
        return findByIdAndUserId(id, userId)!!
    }

    /** Writes all mutable fields from the DTO (service has already applied its partial updates), bumps version. */
    fun update(annotation: Annotation): Annotation {
        dsl.update(t)
            .set(t.CFI, annotation.cfi)
            .set(t.TEXT, annotation.text)
            .set(t.COLOR, annotation.color)
            .set(t.STYLE, annotation.style)
            .set(t.NOTE, annotation.note)
            .set(t.CHAPTER_TITLE, annotation.chapterTitle)
            .set(t.VERSION, t.VERSION.plus(1L))
            .set(t.UPDATED_AT, LocalDateTime.now())
            .where(t.ID.eq(annotation.id))
            .execute()
        return findByIdAndUserId(annotation.id, annotation.userId)!!
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    private fun toDto(r: AnnotationsRecord): Annotation =
        Annotation.builder()
            .id(r.id)
            .userId(r.userId)
            .bookId(r.bookId)
            .cfi(r.cfi)
            .text(r.text)
            .color(r.color)
            .style(r.style)
            .note(r.note)
            .chapterTitle(r.chapterTitle)
            .createdAt(r.createdAt)
            .updatedAt(r.updatedAt)
            .build()
}
