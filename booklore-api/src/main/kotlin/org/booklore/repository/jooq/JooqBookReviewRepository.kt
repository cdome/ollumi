package org.booklore.repository.jooq

import org.booklore.jooq.tables.PublicBookReview.PUBLIC_BOOK_REVIEW
import org.booklore.jooq.tables.records.PublicBookReviewRecord
import org.booklore.model.dto.BookReview
import org.booklore.model.enums.MetadataProvider
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

/**
 * jOOQ read/delete access to public_book_review, replacing the JPA BookReviewRepository. Returns the
 * BookReview DTO directly (drops BookReviewMapper). The rows are still WRITTEN via the Hibernate
 * BookMetadataEntity.reviews cascade (BookReviewUpdateService), so BookReviewEntity stays; callers must
 * flush that cascade (saveAndFlush) before reading here in the same transaction.
 */
@Repository
class JooqBookReviewRepository(private val dsl: DSLContext) {

    private val t = PUBLIC_BOOK_REVIEW

    fun findByBookId(bookId: Long): List<BookReview> =
        dsl.selectFrom(t).where(t.BOOK_ID.eq(bookId)).fetch { toDto(it) }

    fun existsById(id: Long): Boolean = dsl.fetchExists(t, t.ID.eq(id))

    fun deleteById(id: Long): Int = dsl.deleteFrom(t).where(t.ID.eq(id)).execute()

    fun deleteByBookId(bookId: Long): Int = dsl.deleteFrom(t).where(t.BOOK_ID.eq(bookId)).execute()

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
