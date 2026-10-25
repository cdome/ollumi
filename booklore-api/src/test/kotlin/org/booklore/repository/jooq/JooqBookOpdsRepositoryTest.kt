package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookShelfMapping.BOOK_SHELF_MAPPING
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Shelf.SHELF
import org.booklore.jooq.tables.Users.USERS
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime

class JooqBookOpdsRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repository: JooqBookOpdsRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private var lib1Id = 0L
    private var lib2Id = 0L
    private var book1Id = 0L   // lib1, author=Tolkien, series=LOTR #1, searchText contains "hobbit"
    private var book2Id = 0L   // lib1, author=Tolkien, series=LOTR #2, searchText contains "ring"
    private var book3Id = 0L   // lib2, author=Orwell, no series, searchText contains "dystopia"
    private var deletedBookId = 0L  // lib1, deleted
    private var shelf1Id = 0L
    private var shelf2Id = 0L
    private var userId = 0L

    @BeforeEach
    fun setUp() {
        // Clean in dependency order
        dsl.deleteFrom(BOOK_SHELF_MAPPING).execute()
        dsl.deleteFrom(BOOK_METADATA_AUTHOR_MAPPING).execute()
        dsl.deleteFrom(AUTHOR).execute()
        dsl.deleteFrom(BOOK_METADATA).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(SHELF).execute()
        dsl.deleteFrom(LIBRARY).execute()
        dsl.deleteFrom(USERS).execute()

        // User (needed for shelf FK)
        userId = dsl.insertInto(USERS)
            .set(USERS.USERNAME, "testuser")
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, "Test User")
            .returningResult(USERS.ID)
            .fetchOne()!!.get(USERS.ID)!!

        // Libraries
        lib1Id = dsl.insertInto(LIBRARY)
            .set(LIBRARY.NAME, "Fantasy Library")
            .returningResult(LIBRARY.ID)
            .fetchOne()!!.get(LIBRARY.ID)!!

        lib2Id = dsl.insertInto(LIBRARY)
            .set(LIBRARY.NAME, "SciFi Library")
            .returningResult(LIBRARY.ID)
            .fetchOne()!!.get(LIBRARY.ID)!!

        val now = LocalDateTime.of(2026, 1, 1, 12, 0)

        // Books
        book1Id = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, lib1Id)
            .set(BOOK.ADDED_ON, now.minusDays(2))
            .returningResult(BOOK.ID)
            .fetchOne()!!.get(BOOK.ID)!!

        book2Id = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, lib1Id)
            .set(BOOK.ADDED_ON, now.minusDays(1))
            .returningResult(BOOK.ID)
            .fetchOne()!!.get(BOOK.ID)!!

        book3Id = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, lib2Id)
            .set(BOOK.ADDED_ON, now)
            .returningResult(BOOK.ID)
            .fetchOne()!!.get(BOOK.ID)!!

        deletedBookId = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, lib1Id)
            .set(BOOK.ADDED_ON, now.plusDays(1))
            .set(BOOK.DELETED, 1.toByte())
            .returningResult(BOOK.ID)
            .fetchOne()!!.get(BOOK.ID)!!

        // Metadata
        dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, book1Id)
            .set(BOOK_METADATA.TITLE, "The Hobbit")
            .set(BOOK_METADATA.SEARCH_TEXT, "hobbit tolkien fantasy")
            .set(BOOK_METADATA.SERIES_NAME, "Lord of the Rings")
            .set(BOOK_METADATA.SERIES_NUMBER, 1.0)
            .execute()

        dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, book2Id)
            .set(BOOK_METADATA.TITLE, "The Two Towers")
            .set(BOOK_METADATA.SEARCH_TEXT, "ring tolkien fantasy")
            .set(BOOK_METADATA.SERIES_NAME, "Lord of the Rings")
            .set(BOOK_METADATA.SERIES_NUMBER, 2.0)
            .execute()

        dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, book3Id)
            .set(BOOK_METADATA.TITLE, "1984")
            .set(BOOK_METADATA.SEARCH_TEXT, "dystopia orwell scifi")
            .execute()

        dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, deletedBookId)
            .set(BOOK_METADATA.TITLE, "Deleted Book")
            .set(BOOK_METADATA.SEARCH_TEXT, "deleted tolkien")
            .execute()

        // Authors
        val tolkienId = dsl.insertInto(AUTHOR)
            .set(AUTHOR.NAME, "J.R.R. Tolkien")
            .returningResult(AUTHOR.ID)
            .fetchOne()!!.get(AUTHOR.ID)!!

        val orwellId = dsl.insertInto(AUTHOR)
            .set(AUTHOR.NAME, "George Orwell")
            .returningResult(AUTHOR.ID)
            .fetchOne()!!.get(AUTHOR.ID)!!

        // Author mappings
        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, book1Id)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, tolkienId)
            .execute()

        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, book2Id)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, tolkienId)
            .execute()

        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, book3Id)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, orwellId)
            .execute()

        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, deletedBookId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, tolkienId)
            .execute()

        // Shelves
        shelf1Id = dsl.insertInto(SHELF)
            .set(SHELF.USER_ID, userId)
            .set(SHELF.NAME, "Favorites")
            .returningResult(SHELF.ID)
            .fetchOne()!!.get(SHELF.ID)!!

        shelf2Id = dsl.insertInto(SHELF)
            .set(SHELF.USER_ID, userId)
            .set(SHELF.NAME, "To Read")
            .returningResult(SHELF.ID)
            .fetchOne()!!.get(SHELF.ID)!!

        // Shelf mappings: book1 on shelf1, book2 on shelf1+shelf2, book3 on shelf2
        dsl.insertInto(BOOK_SHELF_MAPPING)
            .set(BOOK_SHELF_MAPPING.BOOK_ID, book1Id)
            .set(BOOK_SHELF_MAPPING.SHELF_ID, shelf1Id)
            .execute()

        dsl.insertInto(BOOK_SHELF_MAPPING)
            .set(BOOK_SHELF_MAPPING.BOOK_ID, book2Id)
            .set(BOOK_SHELF_MAPPING.SHELF_ID, shelf1Id)
            .execute()

        dsl.insertInto(BOOK_SHELF_MAPPING)
            .set(BOOK_SHELF_MAPPING.BOOK_ID, book2Id)
            .set(BOOK_SHELF_MAPPING.SHELF_ID, shelf2Id)
            .execute()

        dsl.insertInto(BOOK_SHELF_MAPPING)
            .set(BOOK_SHELF_MAPPING.BOOK_ID, book3Id)
            .set(BOOK_SHELF_MAPPING.SHELF_ID, shelf2Id)
            .execute()
    }

    // ========================================================================
    // findBookIds
    // ========================================================================

    @Test
    fun `findBookIds returns all non-deleted books ordered by addedOn desc`() {
        val page = repository.findBookIds(PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).containsExactly(book3Id, book2Id, book1Id)
    }

    @Test
    fun `findBookIds respects pagination`() {
        val page = repository.findBookIds(PageRequest.of(0, 2))
        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).hasSize(2)
        assertThat(page.content).containsExactly(book3Id, book2Id)

        val page2 = repository.findBookIds(PageRequest.of(1, 2))
        assertThat(page2.content).containsExactly(book1Id)
    }

    @Test
    fun `findBookIds excludes deleted books`() {
        val page = repository.findBookIds(PageRequest.of(0, 100))
        assertThat(page.content).doesNotContain(deletedBookId)
    }

    // ========================================================================
    // findBookIdsByLibraryIds
    // ========================================================================

    @Test
    fun `findBookIdsByLibraryIds filters by library`() {
        val page = repository.findBookIdsByLibraryIds(listOf(lib1Id), PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactly(book2Id, book1Id)
    }

    @Test
    fun `findBookIdsByLibraryIds supports multiple libraries`() {
        val page = repository.findBookIdsByLibraryIds(listOf(lib1Id, lib2Id), PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(3)
    }

    // ========================================================================
    // findBookIdsByShelfId / findBookIdsByShelfIds
    // ========================================================================

    @Test
    fun `findBookIdsByShelfId returns books on shelf`() {
        val page = repository.findBookIdsByShelfId(shelf1Id, PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactly(book2Id, book1Id)
    }

    @Test
    fun `findBookIdsByShelfIds returns distinct books across shelves`() {
        // book2 is on both shelves — should appear once
        val page = repository.findBookIdsByShelfIds(listOf(shelf1Id, shelf2Id), PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).containsExactly(book3Id, book2Id, book1Id)
    }

    // ========================================================================
    // findBookIdsByMetadataSearch
    // ========================================================================

    @Test
    fun `findBookIdsByMetadataSearch matches text`() {
        val page = repository.findBookIdsByMetadataSearch("tolkien", PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id)
    }

    @Test
    fun `findBookIdsByMetadataSearch does not match deleted books`() {
        // deletedBookId has "tolkien" in search_text but is deleted
        val page = repository.findBookIdsByMetadataSearch("deleted", PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(0)
    }

    @Test
    fun `findBookIdsByMetadataSearchAndLibraryIds filters by library`() {
        val page = repository.findBookIdsByMetadataSearchAndLibraryIds(
            "tolkien", listOf(lib1Id), PageRequest.of(0, 10)
        )
        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id)
    }

    @Test
    fun `findBookIdsByMetadataSearchAndShelfIds filters by shelf`() {
        val page = repository.findBookIdsByMetadataSearchAndShelfIds(
            "tolkien", listOf(shelf1Id), PageRequest.of(0, 10)
        )
        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactlyInAnyOrder(book1Id, book2Id)

        val page2 = repository.findBookIdsByMetadataSearchAndShelfIds(
            "dystopia", listOf(shelf1Id), PageRequest.of(0, 10)
        )
        assertThat(page2.totalElements).isEqualTo(0) // book3 (dystopia) is on shelf2, not shelf1
    }

    // ========================================================================
    // findBookIdsByAuthorName
    // ========================================================================

    @Test
    fun `findBookIdsByAuthorName returns books by author`() {
        val page = repository.findBookIdsByAuthorName("J.R.R. Tolkien", PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactly(book2Id, book1Id)
    }

    @Test
    fun `findBookIdsByAuthorName excludes deleted`() {
        // deletedBookId has Tolkien mapping but is deleted
        val page = repository.findBookIdsByAuthorName("J.R.R. Tolkien", PageRequest.of(0, 10))
        assertThat(page.content).doesNotContain(deletedBookId)
    }

    @Test
    fun `findBookIdsByAuthorNameAndLibraryIds filters by library`() {
        val page = repository.findBookIdsByAuthorNameAndLibraryIds(
            "J.R.R. Tolkien", listOf(lib1Id), PageRequest.of(0, 10)
        )
        assertThat(page.totalElements).isEqualTo(2)

        val page2 = repository.findBookIdsByAuthorNameAndLibraryIds(
            "George Orwell", listOf(lib1Id), PageRequest.of(0, 10)
        )
        assertThat(page2.totalElements).isEqualTo(0) // Orwell is in lib2
    }

    // ========================================================================
    // findBookIdsBySeriesName
    // ========================================================================

    @Test
    fun `findBookIdsBySeriesName returns books ordered by series number`() {
        val page = repository.findBookIdsBySeriesName("Lord of the Rings", PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactly(book1Id, book2Id) // series number 1, 2
    }

    @Test
    fun `findBookIdsBySeriesNameAndLibraryIds filters by library`() {
        val page = repository.findBookIdsBySeriesNameAndLibraryIds(
            "Lord of the Rings", listOf(lib2Id), PageRequest.of(0, 10)
        )
        assertThat(page.totalElements).isEqualTo(0) // series is only in lib1
    }

    // ========================================================================
    // findRandomBookIds
    // ========================================================================

    @Test
    fun `findRandomBookIds returns all non-deleted books`() {
        val ids = repository.findRandomBookIds()
        assertThat(ids).containsExactlyInAnyOrder(book1Id, book2Id, book3Id)
        assertThat(ids).doesNotContain(deletedBookId)
    }

    @Test
    fun `findRandomBookIdsByLibraryIds filters by library`() {
        val ids = repository.findRandomBookIdsByLibraryIds(listOf(lib1Id))
        assertThat(ids).containsExactlyInAnyOrder(book1Id, book2Id)
    }

    // ========================================================================
    // findDistinctAuthorNames
    // ========================================================================

    @Test
    fun `findDistinctAuthorNames returns sorted author names excluding deleted books`() {
        val names = repository.findDistinctAuthorNames()
        assertThat(names).containsExactly("George Orwell", "J.R.R. Tolkien")
    }

    @Test
    fun `findDistinctAuthorNamesByLibraryIds filters by library`() {
        val names = repository.findDistinctAuthorNamesByLibraryIds(listOf(lib1Id))
        assertThat(names).containsExactly("J.R.R. Tolkien")
    }

    // ========================================================================
    // findDistinctSeries
    // ========================================================================

    @Test
    fun `findDistinctSeries returns sorted series names`() {
        val names = repository.findDistinctSeries()
        assertThat(names).containsExactly("Lord of the Rings")
    }

    @Test
    fun `findDistinctSeriesByLibraryIds filters by library`() {
        val names = repository.findDistinctSeriesByLibraryIds(listOf(lib2Id))
        assertThat(names).isEmpty()

        val names2 = repository.findDistinctSeriesByLibraryIds(listOf(lib1Id))
        assertThat(names2).containsExactly("Lord of the Rings")
    }
}
