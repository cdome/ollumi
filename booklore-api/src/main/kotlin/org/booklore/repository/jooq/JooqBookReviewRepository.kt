package org.booklore.repository.jooq

import org.booklore.jooq.tables.PublicBookReview.PUBLIC_BOOK_REVIEW
import org.booklore.jooq.tables.records.PublicBookReviewRecord
import org.booklore.model.dto.BookReview
import org.booklore.model.enums.MetadataProvider
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * jOOQ read/write access to public_book_review, replacing the JPA BookReviewEntity/its Hibernate
 * cascade entirely. Returns the BookReview DTO directly (drops BookReviewMapper).
 */
@Repository
class JooqBookReviewRepository(private val dsl: DSLContext) {

    private val t = PUBLIC_BOOK_REVIEW

    fun findByBookId(bookId: Long): List<BookReview> =
        dsl.selectFrom(t).where(t.BOOK_ID.eq(bookId)).fetch { toDto(it) }

    fun existsById(id: Long): Boolean = dsl.fetchExists(t, t.ID.eq(id))

    fun deleteById(id: Long): Int = dsl.deleteFrom(t).where(t.ID.eq(id)).execute()

    fun deleteByBookId(bookId: Long): Int = dsl.deleteFrom(t).where(t.BOOK_ID.eq(bookId)).execute()

    fun insert(bookId: Long, review: BookReview) {
        dsl.insertInto(t)
            .set(t.BOOK_ID, bookId)
            .set(t.METADATA_PROVIDER, review.metadataProvider?.name)
            .set(t.REVIEWER_NAME, review.reviewerName)
            .set(t.TITLE, review.title)
            .set(t.RATING, review.rating?.toDouble())
            .set(t.DATE, review.date?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) })
            .set(t.BODY, review.body)
            .set(t.COUNTRY, review.country)
            .set(t.SPOILER, review.spoiler?.let { if (it) 1.toByte() else 0.toByte() })
            .set(t.FOLLOWERS_COUNT, review.followersCount)
            .set(t.TEXT_REVIEWS_COUNT, review.textReviewsCount)
            .execute()
    }

    private fun toDto(r: PublicBookReviewRecord): BookReview =
        BookReview.builder()
            .id(r.id)
            .metadataProvider(r.metadataProvider?.let { MetadataProvider.valueOf(it) })
            .reviewerName(r.reviewerName)
            .title(r.title)
            .rating(r.rating?.toFloat())
            .date(r.date?.toInstant(ZoneOffset.UTC))
            .body(r.body)
            .country(r.country)
            .spoiler(r.spoiler?.let { it == 1.toByte() })
            .followersCount(r.followersCount)
            .textReviewsCount(r.textReviewsCount)
            .build()
}
