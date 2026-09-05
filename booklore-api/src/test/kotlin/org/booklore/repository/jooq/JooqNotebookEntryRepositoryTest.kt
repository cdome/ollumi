package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Annotations.ANNOTATIONS
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMarks.BOOK_MARKS
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookNotesV2.BOOK_NOTES_V2
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

class JooqNotebookEntryRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repository: JooqNotebookEntryRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private var userId = 0L
    private var otherUserId = 0L
    private var book1Id = 0L
    private var book2Id = 0L

    @BeforeEach
    fun setUp() {
        // Clean in dependency order (children first)
        dsl.deleteFrom(ANNOTATIONS).execute()
        dsl.deleteFrom(BOOK_NOTES_V2).execute()
        dsl.deleteFrom(BOOK_MARKS).execute()
        dsl.deleteFrom(BOOK_FILE).execute()
        dsl.deleteFrom(BOOK_METADATA).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY).execute()
        dsl.deleteFrom(USERS).execute()

        // Users
        userId = dsl.insertInto(USERS)
            .set(USERS.USERNAME, "testuser")
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, "Test User")
            .returningResult(USERS.ID)
            .fetchOne()!!.get(USERS.ID)!!

        otherUserId = dsl.insertInto(USERS)
            .set(USERS.USERNAME, "other")
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, "Other User")
            .returningResult(USERS.ID)
            .fetchOne()!!.get(USERS.ID)!!

        // Library
        val libraryId = dsl.insertInto(LIBRARY)
            .set(LIBRARY.NAME, "Test Library")
            .returningResult(LIBRARY.ID)
            .fetchOne()!!.get(LIBRARY.ID)!!

        // Books
        book1Id = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .returningResult(BOOK.ID)
            .fetchOne()!!.get(BOOK.ID)!!

        book2Id = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .returningResult(BOOK.ID)
            .fetchOne()!!.get(BOOK.ID)!!

        // Book metadata
        dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, book1Id)
            .set(BOOK_METADATA.TITLE, "The Great Gatsby")
            .set(BOOK_METADATA.COVER_UPDATED_ON, LocalDateTime.of(2026, 1, 15, 12, 0))
            .execute()

        dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, book2Id)
            .set(BOOK_METADATA.TITLE, "1984")
            .set(BOOK_METADATA.COVER_UPDATED_ON, LocalDateTime.of(2026, 2, 10, 8, 0))
            .execute()

        // Book file (for primaryBookType subquery)
        dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, book1Id)
            .set(BOOK_FILE.FILE_NAME, "gatsby.epub")
            .set(BOOK_FILE.FILE_SUB_PATH, "/books/gatsby.epub")
            .set(BOOK_FILE.BOOK_TYPE, "EPUB")
            .execute()

        dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, book2Id)
            .set(BOOK_FILE.FILE_NAME, "1984.pdf")
            .set(BOOK_FILE.FILE_SUB_PATH, "/books/1984.pdf")
            .set(BOOK_FILE.BOOK_TYPE, "PDF")
            .execute()

        val now = LocalDateTime.of(2026, 3, 1, 10, 0)

        // Annotations (highlights) — 2 for book1, 1 for book2
        dsl.insertInto(ANNOTATIONS)
            .set(ANNOTATIONS.USER_ID, userId)
            .set(ANNOTATIONS.BOOK_ID, book1Id)
            .set(ANNOTATIONS.CFI, "epubcfi(/6/4)")
            .set(ANNOTATIONS.TEXT, "So we beat on, boats against the current")
            .set(ANNOTATIONS.NOTE, "Famous closing line")
            .set(ANNOTATIONS.COLOR, "#FFFF00")
            .set(ANNOTATIONS.STYLE, "highlight")
            .set(ANNOTATIONS.CHAPTER_TITLE, "Chapter 9")
            .set(ANNOTATIONS.CREATED_AT, now)
            .set(ANNOTATIONS.UPDATED_AT, now)
            .execute()

        dsl.insertInto(ANNOTATIONS)
            .set(ANNOTATIONS.USER_ID, userId)
            .set(ANNOTATIONS.BOOK_ID, book1Id)
            .set(ANNOTATIONS.CFI, "epubcfi(/6/2)")
            .set(ANNOTATIONS.TEXT, "In my younger and more vulnerable years")
            .set(ANNOTATIONS.COLOR, "#00FF00")
            .set(ANNOTATIONS.CHAPTER_TITLE, "Chapter 1")
            .set(ANNOTATIONS.CREATED_AT, now.plusHours(1))
            .set(ANNOTATIONS.UPDATED_AT, now.plusHours(1))
            .execute()

        dsl.insertInto(ANNOTATIONS)
            .set(ANNOTATIONS.USER_ID, userId)
            .set(ANNOTATIONS.BOOK_ID, book2Id)
            .set(ANNOTATIONS.CFI, "epubcfi(/4/2)")
            .set(ANNOTATIONS.TEXT, "Big Brother is watching you")
            .set(ANNOTATIONS.COLOR, "#FF0000")
            .set(ANNOTATIONS.CHAPTER_TITLE, "Part 1")
            .set(ANNOTATIONS.CREATED_AT, now.plusHours(2))
            .set(ANNOTATIONS.UPDATED_AT, now.plusHours(2))
            .execute()

        // Book notes — 1 for book1
        dsl.insertInto(BOOK_NOTES_V2)
            .set(BOOK_NOTES_V2.USER_ID, userId)
            .set(BOOK_NOTES_V2.BOOK_ID, book1Id)
            .set(BOOK_NOTES_V2.CFI, "epubcfi(/6/6)")
            .set(BOOK_NOTES_V2.SELECTED_TEXT, "Gatsby believed in the green light")
            .set(BOOK_NOTES_V2.NOTE_CONTENT, "Symbolism of the green light")
            .set(BOOK_NOTES_V2.COLOR, "#00FF00")
            .set(BOOK_NOTES_V2.CHAPTER_TITLE, "Chapter 5")
            .set(BOOK_NOTES_V2.CREATED_AT, now.plusHours(3))
            .set(BOOK_NOTES_V2.UPDATED_AT, now.plusHours(3))
            .execute()

        // Bookmarks — 1 for book2
        dsl.insertInto(BOOK_MARKS)
            .set(BOOK_MARKS.USER_ID, userId)
            .set(BOOK_MARKS.BOOK_ID, book2Id)
            .set(BOOK_MARKS.CFI, "epubcfi(/8/2)")
            .set(BOOK_MARKS.TITLE, "War is Peace")
            .set(BOOK_MARKS.NOTES, "Party slogan")
            .set(BOOK_MARKS.COLOR, "#0000FF")
            .set(BOOK_MARKS.CREATED_AT, now.plusHours(4))
            .set(BOOK_MARKS.UPDATED_AT, now.plusHours(4))
            .execute()

        // Another user's annotation (should not appear in queries for userId)
        dsl.insertInto(ANNOTATIONS)
            .set(ANNOTATIONS.USER_ID, otherUserId)
            .set(ANNOTATIONS.BOOK_ID, book1Id)
            .set(ANNOTATIONS.CFI, "epubcfi(/6/10)")
            .set(ANNOTATIONS.TEXT, "Other user's highlight")
            .set(ANNOTATIONS.COLOR, "#AAAAAA")
            .set(ANNOTATIONS.CREATED_AT, now.plusHours(5))
            .set(ANNOTATIONS.UPDATED_AT, now.plusHours(5))
            .execute()
    }

    // ========================================================================
    // findEntries
    // ========================================================================

    @Test
    fun `findEntries returns all types for user`() {
        val page = repository.findEntries(
            userId, setOf("HIGHLIGHT", "NOTE", "BOOKMARK"), null, null,
            PageRequest.of(0, 20, Sort.by("createdAt").descending())
        )

        assertThat(page.totalElements).isEqualTo(5)
        assertThat(page.content).hasSize(5)
        // Most recent first
        assertThat(page.content[0].type).isEqualTo("BOOKMARK")
        assertThat(page.content[1].type).isEqualTo("NOTE")
        assertThat(page.content[4].type).isEqualTo("HIGHLIGHT")
    }

    @Test
    fun `findEntries filters by type`() {
        val page = repository.findEntries(
            userId, setOf("HIGHLIGHT"), null, null,
            PageRequest.of(0, 20, Sort.by("createdAt").descending())
        )

        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).allSatisfy { assertThat(it.type).isEqualTo("HIGHLIGHT") }
    }

    @Test
    fun `findEntries filters by bookId`() {
        val page = repository.findEntries(
            userId, setOf("HIGHLIGHT", "NOTE", "BOOKMARK"), book1Id, null,
            PageRequest.of(0, 20, Sort.by("createdAt").descending())
        )

        assertThat(page.totalElements).isEqualTo(3) // 2 highlights + 1 note
        assertThat(page.content).allSatisfy { assertThat(it.bookId).isEqualTo(book1Id) }
    }

    @Test
    fun `findEntries filters by search in text`() {
        val page = repository.findEntries(
            userId, setOf("HIGHLIGHT", "NOTE", "BOOKMARK"), null, "%green light%",
            PageRequest.of(0, 20, Sort.by("createdAt").descending())
        )

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content[0].type).isEqualTo("NOTE")
        assertThat(page.content[0].text).isEqualTo("Gatsby believed in the green light")
    }

    @Test
    fun `findEntries filters by search in note`() {
        val page = repository.findEntries(
            userId, setOf("HIGHLIGHT", "NOTE", "BOOKMARK"), null, "%closing line%",
            PageRequest.of(0, 20, Sort.by("createdAt").descending())
        )

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content[0].note).isEqualTo("Famous closing line")
    }

    @Test
    fun `findEntries filters by search in book title`() {
        val page = repository.findEntries(
            userId, setOf("HIGHLIGHT", "NOTE", "BOOKMARK"), null, "%Gatsby%",
            PageRequest.of(0, 20, Sort.by("createdAt").descending())
        )

        assertThat(page.totalElements).isEqualTo(3) // all book1 entries
        assertThat(page.content).allSatisfy {
            assertThat(it.bookTitle).isEqualTo("The Great Gatsby")
        }
    }

    @Test
    fun `findEntries paginates correctly`() {
        val page1 = repository.findEntries(
            userId, setOf("HIGHLIGHT", "NOTE", "BOOKMARK"), null, null,
            PageRequest.of(0, 2, Sort.by("createdAt").descending())
        )
        val page2 = repository.findEntries(
            userId, setOf("HIGHLIGHT", "NOTE", "BOOKMARK"), null, null,
            PageRequest.of(1, 2, Sort.by("createdAt").descending())
        )

        assertThat(page1.totalElements).isEqualTo(5)
        assertThat(page1.content).hasSize(2)
        assertThat(page2.content).hasSize(2)
        // No overlap
        val page1Ids = page1.content.map { it.id }.toSet()
        val page2Ids = page2.content.map { it.id }.toSet()
        assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids)
    }

    @Test
    fun `findEntries sorts by chapter title ascending`() {
        val page = repository.findEntries(
            userId, setOf("HIGHLIGHT", "NOTE", "BOOKMARK"), book1Id, null,
            PageRequest.of(0, 20, Sort.by(Sort.Order.asc("chapterTitle"), Sort.Order.asc("createdAt")))
        )

        assertThat(page.content).hasSize(3)
        assertThat(page.content[0].chapterTitle).isEqualTo("Chapter 1")
        assertThat(page.content[1].chapterTitle).isEqualTo("Chapter 5")
        assertThat(page.content[2].chapterTitle).isEqualTo("Chapter 9")
    }

    @Test
    fun `findEntries returns primaryBookType from book file`() {
        val page = repository.findEntries(
            userId, setOf("HIGHLIGHT"), book1Id, null,
            PageRequest.of(0, 20, Sort.by("createdAt").descending())
        )

        assertThat(page.content).isNotEmpty
        assertThat(page.content).allSatisfy {
            assertThat(it.primaryBookType).isEqualTo("EPUB")
        }
    }

    @Test
    fun `findEntries excludes other users entries`() {
        val page = repository.findEntries(
            userId, setOf("HIGHLIGHT", "NOTE", "BOOKMARK"), null, null,
            PageRequest.of(0, 20, Sort.by("createdAt").descending())
        )

        assertThat(page.totalElements).isEqualTo(5)
        assertThat(page.content).noneMatch { it.text == "Other user's highlight" }
    }

    @Test
    fun `findEntries returns correct fields for each type`() {
        // Highlight should have style
        val highlights = repository.findEntries(
            userId, setOf("HIGHLIGHT"), book1Id, null,
            PageRequest.of(0, 1, Sort.by("createdAt").ascending())
        )
        assertThat(highlights.content[0].style).isEqualTo("highlight")
        assertThat(highlights.content[0].color).isEqualTo("#FFFF00")

        // Note should have null style
        val notes = repository.findEntries(
            userId, setOf("NOTE"), null, null,
            PageRequest.of(0, 1, Sort.by("createdAt").ascending())
        )
        assertThat(notes.content[0].style).isNull()

        // Bookmark should have null style and null chapterTitle
        val bookmarks = repository.findEntries(
            userId, setOf("BOOKMARK"), null, null,
            PageRequest.of(0, 1, Sort.by("createdAt").ascending())
        )
        assertThat(bookmarks.content[0].style).isNull()
        assertThat(bookmarks.content[0].chapterTitle).isNull()
    }

    // ========================================================================
    // findBooksWithAnnotations
    // ========================================================================

    @Test
    fun `findBooksWithAnnotations returns distinct books for user`() {
        val books = repository.findBooksWithAnnotations(userId, null, Pageable.ofSize(50))

        assertThat(books).hasSize(2)
        assertThat(books.map { it.bookTitle }).containsExactly("1984", "The Great Gatsby")
    }

    @Test
    fun `findBooksWithAnnotations filters by search`() {
        val books = repository.findBooksWithAnnotations(userId, "%Gatsby%", Pageable.ofSize(50))

        assertThat(books).hasSize(1)
        assertThat(books[0].bookTitle).isEqualTo("The Great Gatsby")
    }

    @Test
    fun `findBooksWithAnnotations excludes other users books`() {
        // Create a book with only other user's annotations
        val otherBooks = repository.findBooksWithAnnotations(otherUserId, null, Pageable.ofSize(50))

        assertThat(otherBooks).hasSize(1) // only book1 (the other user has one annotation there)
    }

    // ========================================================================
    // findBooksWithAnnotationsPaginated
    // ========================================================================

    @Test
    fun `findBooksWithAnnotationsPaginated returns books with counts`() {
        val page = repository.findBooksWithAnnotationsPaginated(userId, null, PageRequest.of(0, 20))

        assertThat(page.totalElements).isEqualTo(2)
        val byTitle = page.content.associateBy { it.bookTitle }
        // book1 has: 2 highlights + 1 note = 3
        assertThat(byTitle["The Great Gatsby"]!!.noteCount).isEqualTo(3)
        // book2 has: 1 highlight + 1 bookmark = 2
        assertThat(byTitle["1984"]!!.noteCount).isEqualTo(2)
    }

    @Test
    fun `findBooksWithAnnotationsPaginated returns coverUpdatedOn`() {
        val page = repository.findBooksWithAnnotationsPaginated(userId, null, PageRequest.of(0, 20))

        val gatsby = page.content.first { it.bookTitle == "The Great Gatsby" }
        assertThat(gatsby.coverUpdatedOn).isEqualTo(LocalDateTime.of(2026, 1, 15, 12, 0))
    }

    @Test
    fun `findBooksWithAnnotationsPaginated filters by search`() {
        val page = repository.findBooksWithAnnotationsPaginated(userId, "%1984%", PageRequest.of(0, 20))

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content[0].bookTitle).isEqualTo("1984")
    }

    @Test
    fun `findBooksWithAnnotationsPaginated paginates correctly`() {
        val page1 = repository.findBooksWithAnnotationsPaginated(userId, null, PageRequest.of(0, 1))
        val page2 = repository.findBooksWithAnnotationsPaginated(userId, null, PageRequest.of(1, 1))

        assertThat(page1.totalElements).isEqualTo(2)
        assertThat(page1.content).hasSize(1)
        assertThat(page2.content).hasSize(1)
        assertThat(page1.content[0].bookTitle).isNotEqualTo(page2.content[0].bookTitle)
    }

    @Test
    fun `findBooksWithAnnotationsPaginated orders by book title`() {
        val page = repository.findBooksWithAnnotationsPaginated(userId, null, PageRequest.of(0, 20))

        assertThat(page.content.map { it.bookTitle }).containsExactly("1984", "The Great Gatsby")
    }
}
