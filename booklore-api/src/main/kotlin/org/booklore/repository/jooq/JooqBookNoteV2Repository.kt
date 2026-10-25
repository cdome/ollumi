package org.booklore.repository.jooq

import org.booklore.jooq.tables.BookNotesV2.BOOK_NOTES_V2
import org.booklore.jooq.tables.records.BookNotesV2Record
import org.booklore.model.dto.BookNoteV2
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqBookNoteV2Repository(private val dsl: DSLContext) {

    private val t = BOOK_NOTES_V2

    fun findByBookIdAndUserIdOrderByCreatedAtDesc(bookId: Long, userId: Long): List<BookNoteV2> =
        dsl.selectFrom(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .orderBy(t.CREATED_AT.desc())
            .fetch()
            .map(::toDto)

    fun findByIdAndUserId(id: Long, userId: Long): BookNoteV2? =
        dsl.selectFrom(t)
            .where(t.ID.eq(id).and(t.USER_ID.eq(userId)))
            .fetchOne()?.let(::toDto)

    fun existsByCfiAndBookIdAndUserId(cfi: String, bookId: Long, userId: Long): Boolean =
        dsl.fetchExists(t, t.CFI.eq(cfi).and(t.BOOK_ID.eq(bookId)).and(t.USER_ID.eq(userId)))

    fun insert(
        bookId: Long,
        userId: Long,
        cfi: String,
        selectedText: String?,
        noteContent: String,
        color: String,
        chapterTitle: String?,
    ): BookNoteV2 {
        val now = LocalDateTime.now()
        val id = dsl.insertInto(t)
            .set(t.BOOK_ID, bookId)
            .set(t.USER_ID, userId)
            .set(t.CFI, cfi)
            .set(t.SELECTED_TEXT, selectedText)
            .set(t.NOTE_CONTENT, noteContent)
            .set(t.COLOR, color)
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
    fun update(note: BookNoteV2): BookNoteV2 {
        dsl.update(t)
            .set(t.CFI, note.cfi)
            .set(t.SELECTED_TEXT, note.selectedText)
            .set(t.NOTE_CONTENT, note.noteContent)
            .set(t.COLOR, note.color)
            .set(t.CHAPTER_TITLE, note.chapterTitle)
            .set(t.VERSION, t.VERSION.plus(1L))
            .set(t.UPDATED_AT, LocalDateTime.now())
            .where(t.ID.eq(note.id))
            .execute()
        return findByIdAndUserId(note.id, note.userId)!!
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    private fun toDto(r: BookNotesV2Record): BookNoteV2 =
        BookNoteV2.builder()
            .id(r.id)
            .userId(r.userId)
            .bookId(r.bookId)
            .cfi(r.cfi)
            .selectedText(r.selectedText)
            .noteContent(r.noteContent)
            .color(r.color)
            .chapterTitle(r.chapterTitle)
            .createdAt(r.createdAt)
            .updatedAt(r.updatedAt)
            .build()
}
