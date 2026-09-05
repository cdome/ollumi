package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.PublicBookReview.PUBLIC_BOOK_REVIEW
import org.booklore.model.enums.MetadataProvider
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class JooqBookReviewRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqBookReviewRepository
    @Autowired private lateinit var dsl: DSLContext

    private var bookId = 0L

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(PUBLIC_BOOK_REVIEW).execute()
        dsl.deleteFrom(BOOK_METADATA).execute()
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY).execute()
        val libId = dsl.insertInto(LIBRARY).set(LIBRARY.NAME, "lib")
            .returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!
        bookId = dsl.insertInto(BOOK).set(BOOK.LIBRARY_ID, libId)
            .returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
        dsl.insertInto(BOOK_METADATA).set(BOOK_METADATA.BOOK_ID, bookId).set(BOOK_METADATA.TITLE, "t").execute()
    }

    private fun insertReview(provider: String, rating: Double?, spoiler: Byte?): Long =
        dsl.insertInto(PUBLIC_BOOK_REVIEW)
            .set(PUBLIC_BOOK_REVIEW.BOOK_ID, bookId)
            .set(PUBLIC_BOOK_REVIEW.METADATA_PROVIDER, provider)
            .set(PUBLIC_BOOK_REVIEW.REVIEWER_NAME, "rev")
            .set(PUBLIC_BOOK_REVIEW.TITLE, "title")
            .set(PUBLIC_BOOK_REVIEW.RATING, rating)
            .set(PUBLIC_BOOK_REVIEW.DATE, LocalDateTime.of(2026, 1, 1, 0, 0))
            .set(PUBLIC_BOOK_REVIEW.BODY, "body")
            .set(PUBLIC_BOOK_REVIEW.SPOILER, spoiler)
            .set(PUBLIC_BOOK_REVIEW.FOLLOWERS_COUNT, 5)
            .set(PUBLIC_BOOK_REVIEW.TEXT_REVIEWS_COUNT, 3)
            .returningResult(PUBLIC_BOOK_REVIEW.ID).fetchOne()!!.get(PUBLIC_BOOK_REVIEW.ID)!!

    @Test
    fun `findByBookId maps rows to DTOs with type conversions`() {
        insertReview("Amazon", 4.5, 1)
        insertReview("GoodReads", null, 0)

        val reviews = repository.findByBookId(bookId)
        assertThat(reviews).hasSize(2)

        val amazon = reviews.first { it.metadataProvider == MetadataProvider.Amazon }
        assertThat(amazon.rating).isEqualTo(4.5f)
        assertThat(amazon.spoiler).isTrue()
        assertThat(amazon.followersCount).isEqualTo(5)
        assertThat(amazon.textReviewsCount).isEqualTo(3)
        assertThat(amazon.reviewerName).isEqualTo("rev")
        assertThat(amazon.date).isNotNull()

        val gr = reviews.first { it.metadataProvider == MetadataProvider.GoodReads }
        assertThat(gr.rating as Float?).isNull()
        assertThat(gr.spoiler).isFalse()

        assertThat(repository.findByBookId(999_999L)).isEmpty()
    }

    @Test
    fun `existsById and deleteById`() {
        val id = insertReview("Amazon", 4.0, 0)
        assertThat(repository.existsById(id)).isTrue()
        assertThat(repository.existsById(999_999L)).isFalse()

        assertThat(repository.deleteById(id)).isEqualTo(1)
        assertThat(repository.existsById(id)).isFalse()
    }

    @Test
    fun `deleteByBookId removes all reviews for the book`() {
        insertReview("Amazon", 4.0, 0)
        insertReview("GoodReads", 3.0, 0)

        assertThat(repository.deleteByBookId(bookId)).isEqualTo(2)
        assertThat(repository.findByBookId(bookId)).isEmpty()
    }
}
