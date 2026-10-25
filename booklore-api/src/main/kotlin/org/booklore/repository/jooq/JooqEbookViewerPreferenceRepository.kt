package org.booklore.repository.jooq

import org.booklore.jooq.tables.EbookViewerPreference.EBOOK_VIEWER_PREFERENCE
import org.booklore.jooq.tables.records.EbookViewerPreferenceRecord
import org.booklore.model.dto.EbookViewerPreferences
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqEbookViewerPreferenceRepository(private val dsl: DSLContext) {

    private val t = EBOOK_VIEWER_PREFERENCE

    fun findByBookIdAndUserId(bookId: Long, userId: Long): EbookViewerPreferences? =
        dsl.selectFrom(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId)))
            .fetchOne()?.let(::toDto)

    fun upsert(bookId: Long, userId: Long, p: EbookViewerPreferences) {
        val existingId = dsl.select(t.ID).from(t)
            .where(t.BOOK_ID.eq(bookId).and(t.USER_ID.eq(userId))).fetchOne(t.ID)
        if (existingId != null) {
            dsl.update(t)
                .set(t.FONT_FAMILY, p.fontFamily)
                .set(t.FONT_SIZE, p.fontSize)
                .set(t.GAP, p.gap?.toDouble())
                .set(t.HYPHENATE, p.hyphenate.toByteFlag())
                .set(t.IS_DARK, p.isDark.toByteFlag())
                .set(t.JUSTIFY, p.justify.toByteFlag())
                .set(t.LINE_HEIGHT, p.lineHeight?.toDouble())
                .set(t.MAX_BLOCK_SIZE, p.maxBlockSize)
                .set(t.MAX_COLUMN_COUNT, p.maxColumnCount)
                .set(t.MAX_INLINE_SIZE, p.maxInlineSize)
                .set(t.THEME, p.theme)
                .set(t.FLOW, p.flow)
                .where(t.ID.eq(existingId))
                .execute()
        } else {
            dsl.insertInto(t)
                .set(t.BOOK_ID, bookId)
                .set(t.USER_ID, userId)
                .set(t.FONT_FAMILY, p.fontFamily)
                .set(t.FONT_SIZE, p.fontSize)
                .set(t.GAP, p.gap?.toDouble())
                .set(t.HYPHENATE, p.hyphenate.toByteFlag())
                .set(t.IS_DARK, p.isDark.toByteFlag())
                .set(t.JUSTIFY, p.justify.toByteFlag())
                .set(t.LINE_HEIGHT, p.lineHeight?.toDouble())
                .set(t.MAX_BLOCK_SIZE, p.maxBlockSize)
                .set(t.MAX_COLUMN_COUNT, p.maxColumnCount)
                .set(t.MAX_INLINE_SIZE, p.maxInlineSize)
                .set(t.THEME, p.theme)
                .set(t.FLOW, p.flow)
                .execute()
        }
    }

    private fun toDto(r: EbookViewerPreferenceRecord): EbookViewerPreferences =
        EbookViewerPreferences.builder()
            .bookId(r.bookId)
            .userId(r.userId)
            .fontFamily(r.fontFamily)
            .fontSize(r.fontSize)
            .gap(r.get(t.GAP)?.toFloat())
            .hyphenate(r.get(t.HYPHENATE)?.toBooleanFlag())
            .isDark(r.get(t.IS_DARK)?.toBooleanFlag())
            .justify(r.get(t.JUSTIFY)?.toBooleanFlag())
            .lineHeight(r.get(t.LINE_HEIGHT)?.toFloat())
            .maxBlockSize(r.maxBlockSize)
            .maxColumnCount(r.maxColumnCount)
            .maxInlineSize(r.maxInlineSize)
            .theme(r.theme)
            .flow(r.flow)
            .build()

    private fun Boolean?.toByteFlag(): Byte? = this?.let { if (it) 1 else 0 }
    private fun Byte.toBooleanFlag(): Boolean = this.toInt() != 0
}
