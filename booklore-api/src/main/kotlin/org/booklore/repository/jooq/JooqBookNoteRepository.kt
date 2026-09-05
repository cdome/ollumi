package org.booklore.repository.jooq

import org.booklore.jooq.tables.BookNotes.BOOK_NOTES
import org.booklore.jooq.tables.records.BookNotesRecord
import org.booklore.model.dto.BookNote
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqBookNoteRepository(private val dsl: DSLContext) {

    private val t = BOOK_NOTES

    fun findByBookIdAndUserIdOrderByUpdatedAtDesc(bookId: Long, userId: Long): List<BookNote> =
        dsl.selectFrom(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .orderBy(t.UPDATED_AT.desc())
            .fetch()
            .map(::toDto)

    fun findByIdAndUserId(id: Long, userId: Long): BookNote? =
        dsl.selectFrom(t)
            .where(t.ID.eq(id).and(t.USER_ID.eq(userId)))
            .fetchOne()?.let(::toDto)

    fun insert(bookId: Long, userId: Long, title: String?, content: String): BookNote {
        val now = LocalDateTime.now()
        val id = dsl.insertInto(t)
            .set(t.BOOK_ID, bookId)
            .set(t.USER_ID, userId)
            .set(t.TITLE, title)
            .set(t.CONTENT, content)
            .set(t.CREATED_AT, now)
            .set(t.UPDATED_AT, now)
            .returning(t.ID)
            .fetchOne()!!
            .id
        return findByIdAndUserId(id, userId)!!
    }

    fun update(id: Long, userId: Long, title: String?, content: String): BookNote {
        dsl.update(t)
            .set(t.TITLE, title)
            .set(t.CONTENT, content)
            .set(t.UPDATED_AT, LocalDateTime.now())
            .where(t.ID.eq(id))
            .execute()
        return findByIdAndUserId(id, userId)!!
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    fun count(): Long = dsl.fetchCount(t).toLong()

    private fun toDto(r: BookNotesRecord): BookNote =
        BookNote.builder()
            .id(r.id)
            .userId(r.userId)
            .bookId(r.bookId)
            .title(r.title)
            .content(r.content)
            .createdAt(r.createdAt)
            .updatedAt(r.updatedAt)
            .build()
}
