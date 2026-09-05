package org.booklore.repository.jooq

import org.booklore.jooq.tables.CbxViewerPreference.CBX_VIEWER_PREFERENCE
import org.booklore.jooq.tables.records.CbxViewerPreferenceRecord
import org.booklore.model.dto.CbxViewerPreferences
import org.booklore.model.enums.CbxBackgroundColor
import org.booklore.model.enums.CbxPageFitMode
import org.booklore.model.enums.CbxPageScrollMode
import org.booklore.model.enums.CbxPageSpread
import org.booklore.model.enums.CbxPageViewMode
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqCbxViewerPreferenceRepository(private val dsl: DSLContext) {

    private val t = CBX_VIEWER_PREFERENCE

    fun findByBookIdAndUserId(bookId: Long, userId: Long): CbxViewerPreferences? =
        dsl.selectFrom(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .fetchOne()?.let(::toDto)

    fun upsert(bookId: Long, userId: Long, p: CbxViewerPreferences) {
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

    private fun toDto(r: CbxViewerPreferenceRecord): CbxViewerPreferences =
        CbxViewerPreferences.builder()
            .bookId(r.bookId)
            .pageSpread(r.get(t.SPREAD)?.let { CbxPageSpread.valueOf(it) })
            .pageViewMode(r.get(t.VIEW_MODE)?.let { CbxPageViewMode.valueOf(it) })
            .fitMode(r.get(t.FIT_MODE)?.let { CbxPageFitMode.valueOf(it) })
            .scrollMode(r.get(t.SCROLL_MODE)?.let { CbxPageScrollMode.valueOf(it) })
            .backgroundColor(r.get(t.BACKGROUND_COLOR)?.let { CbxBackgroundColor.valueOf(it) })
            .build()
}
