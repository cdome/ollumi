package org.booklore.repository.jooq

import org.booklore.jooq.tables.NewPdfViewerPreference.NEW_PDF_VIEWER_PREFERENCE
import org.booklore.jooq.tables.records.NewPdfViewerPreferenceRecord
import org.booklore.model.dto.NewPdfViewerPreferences
import org.booklore.model.enums.NewPdfBackgroundColor
import org.booklore.model.enums.NewPdfPageFitMode
import org.booklore.model.enums.NewPdfPageScrollMode
import org.booklore.model.enums.NewPdfPageSpread
import org.booklore.model.enums.NewPdfPageViewMode
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqNewPdfViewerPreferenceRepository(private val dsl: DSLContext) {

    private val t = NEW_PDF_VIEWER_PREFERENCE

    fun findByBookIdAndUserId(bookId: Long, userId: Long): NewPdfViewerPreferences? =
        dsl.selectFrom(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .fetchOne()?.let(::toDto)

    fun upsert(bookId: Long, userId: Long, p: NewPdfViewerPreferences) {
        val existingId = dsl.select(t.ID).from(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId))).fetchOne(t.ID)
        if (existingId != null) {
            dsl.update(t)
                .set(t.SPREAD, p.pageSpread?.name)
                .set(t.VIEW_MODE, p.pageViewMode?.name)
                .set(t.FIT_MODE, p.fitMode?.name)
                .set(t.SCROLL_MODE, p.scrollMode?.name)
                .set(t.BACKGROUND_COLOR, p.backgroundColor?.name)
                .where(t.ID.eq(existingId))
                .execute()
        } else {
            dsl.insertInto(t)
                .set(t.BOOK_ID, bookId)
                .set(t.USER_ID, userId)
                .set(t.SPREAD, p.pageSpread?.name)
                .set(t.VIEW_MODE, p.pageViewMode?.name)
                .set(t.FIT_MODE, p.fitMode?.name)
                .set(t.SCROLL_MODE, p.scrollMode?.name)
                .set(t.BACKGROUND_COLOR, p.backgroundColor?.name)
                .execute()
        }
    }

    private fun toDto(r: NewPdfViewerPreferenceRecord): NewPdfViewerPreferences =
        NewPdfViewerPreferences.builder()
            .bookId(r.bookId)
            .pageSpread(r.get(t.SPREAD)?.let { NewPdfPageSpread.valueOf(it) })
            .pageViewMode(r.get(t.VIEW_MODE)?.let { NewPdfPageViewMode.valueOf(it) })
            .fitMode(r.get(t.FIT_MODE)?.let { NewPdfPageFitMode.valueOf(it) })
            .scrollMode(r.get(t.SCROLL_MODE)?.let { NewPdfPageScrollMode.valueOf(it) })
            .backgroundColor(r.get(t.BACKGROUND_COLOR)?.let { NewPdfBackgroundColor.valueOf(it) })
            .build()
}
