package org.booklore.repository.jooq

import org.booklore.jooq.tables.PdfViewerPreference.PDF_VIEWER_PREFERENCE
import org.booklore.jooq.tables.records.PdfViewerPreferenceRecord
import org.booklore.model.dto.PdfViewerPreferences
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqPdfViewerPreferenceRepository(private val dsl: DSLContext) {

    private val t = PDF_VIEWER_PREFERENCE

    fun findByBookIdAndUserId(bookId: Long, userId: Long): PdfViewerPreferences? =
        dsl.selectFrom(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .fetchOne()?.let(::toDto)

    /** Insert-or-update the (user, book) row with the given zoom/spread. */
    fun upsert(bookId: Long, userId: Long, zoom: String?, spread: String?) {
        val existingId = dsl.select(t.ID).from(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId))).fetchOne(t.ID)
        if (existingId != null) {
            dsl.update(t).set(t.ZOOM, zoom).set(t.SPREAD, spread).where(t.ID.eq(existingId)).execute()
        } else {
            dsl.insertInto(t)
                .set(t.BOOK_ID, bookId).set(t.USER_ID, userId)
                .set(t.ZOOM, zoom).set(t.SPREAD, spread)
                .execute()
        }
    }

    private fun toDto(r: PdfViewerPreferenceRecord): PdfViewerPreferences =
        PdfViewerPreferences.builder()
            .bookId(r.bookId)
            .zoom(r.zoom)
            .spread(r.spread)
            .build()
}
