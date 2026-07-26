package org.booklore.repository.jooq

import org.booklore.jooq.tables.CustomFont.CUSTOM_FONT
import org.booklore.jooq.tables.records.CustomFontRecord
import org.booklore.model.enums.FontFormat
import org.booklore.repository.jooq.dto.CustomFont
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqCustomFontRepository(private val dsl: DSLContext) {

    private val t = CUSTOM_FONT

    fun findByUserId(userId: Long): List<CustomFont> =
        dsl.selectFrom(t).where(t.USER_ID.eq(userId)).orderBy(t.ID).fetch().map(::toDomain)

    fun countByUserId(userId: Long): Int =
        dsl.fetchCount(t, t.USER_ID.eq(userId))

    fun findByIdAndUserId(id: Long, userId: Long): CustomFont? =
        dsl.selectFrom(t).where(t.ID.eq(id).and(t.USER_ID.eq(userId))).fetchOne()?.let(::toDomain)

    fun insert(
        userId: Long,
        fontName: String,
        fileName: String,
        originalFileName: String,
        format: FontFormat,
        fileSize: Long,
        uploadedAt: LocalDateTime,
    ): CustomFont {
        val id = dsl.insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.FONT_NAME, fontName)
            .set(t.FILE_NAME, fileName)
            .set(t.ORIGINAL_FILE_NAME, originalFileName)
            .set(t.FORMAT, format.name)
            .set(t.FILE_SIZE, fileSize)
            .set(t.UPLOADED_AT, uploadedAt)
            .returning(t.ID)
            .fetchOne()!!
            .id
        return findByIdAndUserId(id, userId)!!
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    private fun toDomain(r: CustomFontRecord): CustomFont =
        CustomFont(
            id = r.id,
            userId = r.userId,
            fontName = r.fontName,
            fileName = r.fileName,
            originalFileName = r.originalFileName,
            format = FontFormat.valueOf(r.format),
            fileSize = r.fileSize,
            uploadedAt = r.uploadedAt,
        )
}
