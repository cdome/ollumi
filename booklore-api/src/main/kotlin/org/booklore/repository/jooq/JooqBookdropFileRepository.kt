package org.booklore.repository.jooq

import org.booklore.jooq.tables.BookdropFile.BOOKDROP_FILE
import org.booklore.jooq.tables.records.BookdropFileRecord
import org.booklore.model.enums.BookdropFileStatus
import org.booklore.repository.jooq.dto.BookdropFileRow
import org.jooq.DSLContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

@Repository
class JooqBookdropFileRepository(private val dsl: DSLContext) {

    private val t = BOOKDROP_FILE

    // ---------- reads ----------

    fun existsByFilePath(filePath: String): Boolean =
        dsl.fetchExists(dsl.selectFrom(t).where(t.FILE_PATH.eq(filePath)))

    fun findById(id: Long): Optional<BookdropFileRow> =
        Optional.ofNullable(dsl.selectFrom(t).where(t.ID.eq(id)).fetchOne()?.let(::toRow))

    fun findAllById(ids: List<Long>): List<BookdropFileRow> =
        if (ids.isEmpty()) emptyList()
        else dsl.selectFrom(t).where(t.ID.`in`(ids)).fetch().map(::toRow)

    fun findAll(): List<BookdropFileRow> =
        dsl.selectFrom(t).fetch().map(::toRow)

    fun findAll(pageable: Pageable): Page<BookdropFileRow> {
        val total = dsl.fetchCount(t)
        val content = dsl.selectFrom(t)
            .orderBy(*PageableHelper.toOrderFields(pageable.sort, t))
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map(::toRow)
        return PageableHelper.toPage(content, total.toLong(), pageable)
    }

    fun findAllByStatus(status: BookdropFileStatus, pageable: Pageable): Page<BookdropFileRow> {
        val condition = t.STATUS.eq(status.name)
        val total = dsl.fetchCount(t, condition)
        val content = dsl.selectFrom(t)
            .where(condition)
            .orderBy(*PageableHelper.toOrderFields(pageable.sort, t))
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map(::toRow)
        return PageableHelper.toPage(content, total.toLong(), pageable)
    }

    fun countByStatus(status: BookdropFileStatus): Long =
        dsl.fetchCount(t, t.STATUS.eq(status.name)).toLong()

    fun count(): Long = dsl.fetchCount(t).toLong()

    fun findAllIds(): List<Long> =
        dsl.select(t.ID).from(t).fetch(t.ID)

    fun findAllExcludingIdsFlat(excludedIds: List<Long>): List<Long> =
        dsl.select(t.ID).from(t).where(t.ID.notIn(excludedIds)).fetch(t.ID)

    fun findAllFilePathsIn(filePaths: List<String>): List<String> =
        if (filePaths.isEmpty()) emptyList()
        else dsl.select(t.FILE_PATH).from(t).where(t.FILE_PATH.`in`(filePaths)).fetch(t.FILE_PATH)

    // ---------- writes ----------

    fun insert(filePath: String, fileName: String, fileSize: Long?): Long {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        return dsl.insertInto(t)
            .set(t.FILE_PATH, filePath)
            .set(t.FILE_NAME, fileName)
            .set(t.FILE_SIZE, fileSize)
            .set(t.STATUS, BookdropFileStatus.PENDING_REVIEW.name)
            .set(t.CREATED_AT, now)
            .set(t.UPDATED_AT, now)
            .returning(t.ID)
            .fetchOne()!!.id
    }

    fun updateOriginalMetadata(id: Long, originalMetadataJson: String): BookdropFileRow {
        dsl.update(t)
            .set(t.ORIGINAL_METADATA, originalMetadataJson)
            .set(t.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
            .where(t.ID.eq(id))
            .execute()
        return requireById(id)
    }

    fun updateStatus(id: Long, status: BookdropFileStatus): BookdropFileRow {
        dsl.update(t)
            .set(t.STATUS, status.name)
            .set(t.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
            .where(t.ID.eq(id))
            .execute()
        return requireById(id)
    }

    fun updateFetchedMetadataAndStatus(id: Long, fetchedMetadataJson: String, status: BookdropFileStatus): BookdropFileRow {
        dsl.update(t)
            .set(t.FETCHED_METADATA, fetchedMetadataJson)
            .set(t.STATUS, status.name)
            .set(t.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
            .where(t.ID.eq(id))
            .execute()
        return requireById(id)
    }

    fun updateFetchedMetadataForIds(idToJson: Map<Long, String>) {
        if (idToJson.isEmpty()) return
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val batch = idToJson.map { (id, json) ->
            dsl.update(t)
                .set(t.FETCHED_METADATA, json)
                .set(t.UPDATED_AT, now)
                .where(t.ID.eq(id))
        }
        dsl.batch(batch).execute()
    }

    fun deleteAllByFilePathStartingWith(prefix: String): Int =
        dsl.deleteFrom(t).where(t.FILE_PATH.like("$prefix%")).execute()

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    fun deleteAllById(ids: List<Long>) {
        if (ids.isNotEmpty()) dsl.deleteFrom(t).where(t.ID.`in`(ids)).execute()
    }

    // ---------- mapping ----------

    private fun requireById(id: Long): BookdropFileRow =
        toRow(dsl.selectFrom(t).where(t.ID.eq(id)).fetchOne()
            ?: error("Bookdrop file not found: $id"))

    private fun toRow(r: BookdropFileRecord): BookdropFileRow = BookdropFileRow(
        id = r.id,
        filePath = r.filePath,
        fileName = r.fileName,
        fileSize = r.fileSize,
        status = BookdropFileStatus.valueOf(r.status),
        originalMetadata = r.originalMetadata,
        fetchedMetadata = r.fetchedMetadata,
        createdAt = r.createdAt?.toInstant(),
        updatedAt = r.updatedAt?.toInstant(),
    )

    private fun LocalDateTime.toInstant(): Instant = this.toInstant(ZoneOffset.UTC)
}
