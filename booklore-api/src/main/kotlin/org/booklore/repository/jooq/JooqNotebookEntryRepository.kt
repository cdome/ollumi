package org.booklore.repository.jooq

import org.booklore.jooq.tables.Annotations.ANNOTATIONS
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMarks.BOOK_MARKS
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookNotesV2.BOOK_NOTES_V2
import org.booklore.repository.jooq.dto.NotebookBook
import org.booklore.repository.jooq.dto.NotebookBookWithCount
import org.booklore.repository.jooq.dto.NotebookEntryRow
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqNotebookEntryRepository(private val dsl: DSLContext) {

    private val a = ANNOTATIONS
    private val n = BOOK_NOTES_V2
    private val bk = BOOK_MARKS
    private val bm = BOOK_METADATA
    private val bf = BOOK_FILE

    // ========================================================================
    // Entries (unified view across annotations, notes, bookmarks)
    // ========================================================================

    fun findEntries(
        userId: Long,
        types: Set<String>,
        bookId: Long?,
        search: String?,
        pageable: Pageable
    ): Page<NotebookEntryRow> {
        val highlights = select(
            a.ID.`as`("id"), inline("HIGHLIGHT").`as`("type"),
            a.USER_ID.`as`("user_id"), a.BOOK_ID.`as`("book_id"),
            bm.TITLE.`as`("book_title"),
            a.TEXT.`as`("text"), a.NOTE.`as`("note"),
            a.COLOR.`as`("color"), a.STYLE.`as`("style"),
            a.CHAPTER_TITLE.`as`("chapter_title"),
            a.CREATED_AT.`as`("created_at"), a.UPDATED_AT.`as`("updated_at")
        ).from(a).join(bm).on(bm.BOOK_ID.eq(a.BOOK_ID))

        val notes = select(
            n.ID.`as`("id"), inline("NOTE").`as`("type"),
            n.USER_ID.`as`("user_id"), n.BOOK_ID.`as`("book_id"),
            bm.TITLE.`as`("book_title"),
            n.SELECTED_TEXT.`as`("text"), n.NOTE_CONTENT.`as`("note"),
            n.COLOR.`as`("color"), castNull(String::class.java).`as`("style"),
            n.CHAPTER_TITLE.`as`("chapter_title"),
            n.CREATED_AT.`as`("created_at"), n.UPDATED_AT.`as`("updated_at")
        ).from(n).join(bm).on(bm.BOOK_ID.eq(n.BOOK_ID))

        val bookmarks = select(
            bk.ID.`as`("id"), inline("BOOKMARK").`as`("type"),
            bk.USER_ID.`as`("user_id"), bk.BOOK_ID.`as`("book_id"),
            bm.TITLE.`as`("book_title"),
            bk.TITLE.`as`("text"), bk.NOTES.`as`("note"),
            bk.COLOR.`as`("color"), castNull(String::class.java).`as`("style"),
            castNull(String::class.java).`as`("chapter_title"),
            bk.CREATED_AT.`as`("created_at"), bk.UPDATED_AT.`as`("updated_at")
        ).from(bk).join(bm).on(bm.BOOK_ID.eq(bk.BOOK_ID))

        val t = highlights.unionAll(notes).unionAll(bookmarks).asTable("t")

        val tId = t.field("id", Long::class.java)!!
        val tType = t.field("type", String::class.java)!!
        val tUserId = t.field("user_id", Long::class.java)!!
        val tBookId = t.field("book_id", Long::class.java)!!
        val tBookTitle = t.field("book_title", String::class.java)!!
        val tText = t.field("text", String::class.java)!!
        val tNote = t.field("note", String::class.java)!!
        val tColor = t.field("color", String::class.java)!!
        val tStyle = t.field("style", String::class.java)!!
        val tChapter = t.field("chapter_title", String::class.java)!!
        val tCreated = t.field("created_at", LocalDateTime::class.java)!!
        val tUpdated = t.field("updated_at", LocalDateTime::class.java)!!

        val primaryBookType = dsl.select(bf.BOOK_TYPE)
            .from(bf)
            .where(bf.BOOK_ID.eq(tBookId))
            .orderBy(bf.ID.asc())
            .limit(1)
            .asField<String>()

        val condition = tUserId.eq(userId)
            .and(tType.`in`(types))
            .and(if (bookId != null) tBookId.eq(bookId) else noCondition())
            .and(searchLike(search, tText, tNote, tBookTitle, tChapter))

        val total = dsl.selectCount().from(t).where(condition).fetchOne(0, Long::class.java)!!

        val orderBy = pageable.sort.map { order ->
            val f: Field<*> = when (order.property) {
                "chapterTitle" -> tChapter
                else -> tCreated
            }
            if (order.isAscending) f.asc() else f.desc()
        }.toList().ifEmpty { listOf(tCreated.desc()) }

        val content = dsl.select(
            tId, tType, tBookId, tBookTitle, tText, tNote,
            tColor, tStyle, tChapter, primaryBookType, tCreated, tUpdated
        )
            .from(t)
            .where(condition)
            .orderBy(orderBy)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { r ->
                NotebookEntryRow(
                    id = r.get(tId)!!,
                    type = r.get(tType)!!,
                    bookId = r.get(tBookId)!!,
                    bookTitle = r.get(tBookTitle),
                    text = r.get(tText),
                    note = r.get(tNote),
                    color = r.get(tColor),
                    style = r.get(tStyle),
                    chapterTitle = r.get(tChapter),
                    primaryBookType = r.get(primaryBookType),
                    createdAt = r.get(tCreated)!!,
                    updatedAt = r.get(tUpdated)
                )
            }

        return PageableHelper.toPage(content, total, pageable)
    }

    // ========================================================================
    // Books with annotations (dropdown / autocomplete)
    // ========================================================================

    fun findBooksWithAnnotations(
        userId: Long,
        search: String?,
        pageable: Pageable
    ): List<NotebookBook> {
        val union = select(a.BOOK_ID.`as`("book_id"), bm.TITLE.`as`("book_title"))
            .from(a).join(bm).on(bm.BOOK_ID.eq(a.BOOK_ID))
            .where(a.USER_ID.eq(userId))
            .union(
                select(n.BOOK_ID.`as`("book_id"), bm.TITLE.`as`("book_title"))
                    .from(n).join(bm).on(bm.BOOK_ID.eq(n.BOOK_ID))
                    .where(n.USER_ID.eq(userId))
            )
            .union(
                select(bk.BOOK_ID.`as`("book_id"), bm.TITLE.`as`("book_title"))
                    .from(bk).join(bm).on(bm.BOOK_ID.eq(bk.BOOK_ID))
                    .where(bk.USER_ID.eq(userId))
            )

        val t = union.asTable("t")
        val tBookId = t.field("book_id", Long::class.java)!!
        val tBookTitle = t.field("book_title", String::class.java)!!

        return dsl.selectDistinct(tBookId, tBookTitle)
            .from(t)
            .where(searchLike(search, tBookTitle))
            .orderBy(tBookTitle)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { r ->
                NotebookBook(
                    bookId = r.get(tBookId)!!,
                    bookTitle = r.get(tBookTitle)
                )
            }
    }

    // ========================================================================
    // Books with annotation counts (paginated list)
    // ========================================================================

    fun findBooksWithAnnotationsPaginated(
        userId: Long,
        search: String?,
        pageable: Pageable
    ): Page<NotebookBookWithCount> {
        val union = select(
            a.BOOK_ID.`as`("book_id"), a.USER_ID.`as`("user_id"),
            bm.TITLE.`as`("book_title"), bm.COVER_UPDATED_ON.`as`("cover_updated_on")
        ).from(a).join(bm).on(bm.BOOK_ID.eq(a.BOOK_ID))
            .unionAll(
                select(
                    n.BOOK_ID.`as`("book_id"), n.USER_ID.`as`("user_id"),
                    bm.TITLE.`as`("book_title"), bm.COVER_UPDATED_ON.`as`("cover_updated_on")
                ).from(n).join(bm).on(bm.BOOK_ID.eq(n.BOOK_ID))
            )
            .unionAll(
                select(
                    bk.BOOK_ID.`as`("book_id"), bk.USER_ID.`as`("user_id"),
                    bm.TITLE.`as`("book_title"), bm.COVER_UPDATED_ON.`as`("cover_updated_on")
                ).from(bk).join(bm).on(bm.BOOK_ID.eq(bk.BOOK_ID))
            )

        val t = union.asTable("t")
        val tBookId = t.field("book_id", Long::class.java)!!
        val tUserId = t.field("user_id", Long::class.java)!!
        val tBookTitle = t.field("book_title", String::class.java)!!
        val tCoverUpdatedOn = t.field("cover_updated_on", LocalDateTime::class.java)!!
        val noteCount = count()

        val condition = tUserId.eq(userId).and(searchLike(search, tBookTitle))

        val total = dsl.select(countDistinct(tBookId))
            .from(t)
            .where(condition)
            .fetchOne(0, Long::class.java)!!

        val content = dsl.select(tBookId, tBookTitle, noteCount, tCoverUpdatedOn)
            .from(t)
            .where(condition)
            .groupBy(tBookId, tBookTitle, tCoverUpdatedOn)
            .orderBy(tBookTitle)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { r ->
                NotebookBookWithCount(
                    bookId = r.get(tBookId)!!,
                    bookTitle = r.get(tBookTitle),
                    noteCount = r.get(noteCount)!!,
                    coverUpdatedOn = r.get(tCoverUpdatedOn)
                )
            }

        return PageableHelper.toPage(content, total, pageable)
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    @Suppress("UNCHECKED_CAST")
    private fun searchLike(search: String?, vararg fields: Field<*>): Condition {
        if (search == null) return noCondition()
        return or(fields.map { (it as Field<String>).like(search).escape('\\') })
    }
}
