package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookMetadataCategoryMapping.BOOK_METADATA_CATEGORY_MAPPING
import org.booklore.jooq.tables.BookMetadataMoodMapping.BOOK_METADATA_MOOD_MAPPING
import org.booklore.jooq.tables.BookMetadataTagMapping.BOOK_METADATA_TAG_MAPPING
import org.booklore.jooq.tables.Category.CATEGORY
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Mood.MOOD
import org.booklore.jooq.tables.Tag.TAG
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqBookMetadataRelationsRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqBookMetadataRelationsRepository
    @Autowired private lateinit var dsl: DSLContext

    private var bookId = 0L

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(BOOK_METADATA_AUTHOR_MAPPING).execute()
        dsl.deleteFrom(BOOK_METADATA_CATEGORY_MAPPING).execute()
        dsl.deleteFrom(BOOK_METADATA_TAG_MAPPING).execute()
        dsl.deleteFrom(BOOK_METADATA_MOOD_MAPPING).execute()
        dsl.deleteFrom(BOOK_METADATA).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY).execute()
        dsl.deleteFrom(AUTHOR).execute()
        dsl.deleteFrom(CATEGORY).execute()
        dsl.deleteFrom(TAG).execute()
        dsl.deleteFrom(MOOD).execute()

        val libId = dsl.insertInto(LIBRARY).set(LIBRARY.NAME, "lib")
            .returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!
        bookId = dsl.insertInto(BOOK).set(BOOK.LIBRARY_ID, libId)
            .returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
        dsl.insertInto(BOOK_METADATA).set(BOOK_METADATA.BOOK_ID, bookId).set(BOOK_METADATA.TITLE, "t").execute()
    }

    private fun insertAuthor(name: String) =
        dsl.insertInto(AUTHOR).set(AUTHOR.NAME, name).returningResult(AUTHOR.ID).fetchOne()!!.get(AUTHOR.ID)!!

    @Test
    fun `findAuthorNamesByBookId returns names in persisted sort_order`() {
        val zia = insertAuthor("Zed")
        val abe = insertAuthor("Abe")
        // link in a deliberate order: Zed first (sort_order 0), Abe second (sort_order 1)
        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, zia)
            .set(BOOK_METADATA_AUTHOR_MAPPING.SORT_ORDER, 0).execute()
        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, abe)
            .set(BOOK_METADATA_AUTHOR_MAPPING.SORT_ORDER, 1).execute()

        assertThat(repository.findAuthorNamesByBookId(bookId)).containsExactly("Zed", "Abe")
        assertThat(repository.findAuthorNamesByBookId(999_999L)).isEmpty()
    }

    @Test
    fun `setMoodsForBook find-or-creates masters, replaces junctions, and reuses masters`() {
        repository.setMoodsForBook(bookId, listOf("cozy", "tense", ""))
        assertThat(repository.findMoodNamesByBookId(bookId)).containsExactlyInAnyOrder("cozy", "tense")
        assertThat(dsl.fetchCount(MOOD)).isEqualTo(2) // blank skipped

        // Replace with a subset -> junction reduced, master rows kept and reused (no duplicate insert).
        repository.setMoodsForBook(bookId, listOf("cozy"))
        assertThat(repository.findMoodNamesByBookId(bookId)).containsExactly("cozy")
        assertThat(dsl.fetchCount(MOOD)).isEqualTo(2)

        repository.clearMoodsForBook(bookId)
        assertThat(repository.findMoodNamesByBookId(bookId)).isEmpty()
        assertThat(dsl.fetchCount(MOOD)).isEqualTo(2) // masters not deleted
    }

    @Test
    fun `findCategory-Tag-Mood NamesByBookId return the linked names`() {
        val catId = dsl.insertInto(CATEGORY).set(CATEGORY.NAME, "Fantasy").returningResult(CATEGORY.ID).fetchOne()!!.get(CATEGORY.ID)!!
        val tagId = dsl.insertInto(TAG).set(TAG.NAME, "epic").returningResult(TAG.ID).fetchOne()!!.get(TAG.ID)!!
        val moodId = dsl.insertInto(MOOD).set(MOOD.NAME, "cozy").returningResult(MOOD.ID).fetchOne()!!.get(MOOD.ID)!!
        dsl.insertInto(BOOK_METADATA_CATEGORY_MAPPING)
            .set(BOOK_METADATA_CATEGORY_MAPPING.BOOK_ID, bookId).set(BOOK_METADATA_CATEGORY_MAPPING.CATEGORY_ID, catId).execute()
        dsl.insertInto(BOOK_METADATA_TAG_MAPPING)
            .set(BOOK_METADATA_TAG_MAPPING.BOOK_ID, bookId).set(BOOK_METADATA_TAG_MAPPING.TAG_ID, tagId).execute()
        dsl.insertInto(BOOK_METADATA_MOOD_MAPPING)
            .set(BOOK_METADATA_MOOD_MAPPING.BOOK_ID, bookId).set(BOOK_METADATA_MOOD_MAPPING.MOOD_ID, moodId).execute()

        assertThat(repository.findCategoryNamesByBookId(bookId)).containsExactly("Fantasy")
        assertThat(repository.findTagNamesByBookId(bookId)).containsExactly("epic")
        assertThat(repository.findMoodNamesByBookId(bookId)).containsExactly("cozy")
        assertThat(repository.findCategoryNamesByBookId(999_999L)).isEmpty()
    }
}
