package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.BookdropFile.BOOKDROP_FILE
import org.booklore.model.enums.BookdropFileStatus
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest

class JooqBookdropFileRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqBookdropFileRepository
    @Autowired private lateinit var dsl: DSLContext

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(BOOKDROP_FILE).execute()
    }

    @Test
    fun `insert then findById returns a PENDING_REVIEW row with timestamps`() {
        val id = repository.insert("/bookdrop/a.epub", "a.epub", 123L)

        val found = repository.findById(id)
        assertThat(found).isPresent
        val row = found.get()
        assertThat(row.id).isEqualTo(id)
        assertThat(row.filePath).isEqualTo("/bookdrop/a.epub")
        assertThat(row.fileName).isEqualTo("a.epub")
        assertThat(row.fileSize).isEqualTo(123L)
        assertThat(row.status).isEqualTo(BookdropFileStatus.PENDING_REVIEW)
        assertThat(row.originalMetadata).isNull()
        assertThat(row.fetchedMetadata).isNull()
        assertThat(row.createdAt).isNotNull()
        assertThat(row.updatedAt).isNotNull()
    }

    @Test
    fun `insert accepts a null fileSize`() {
        val id = repository.insert("/bookdrop/b.epub", "b.epub", null)
        assertThat(repository.findById(id).get().fileSize).isNull()
    }

    @Test
    fun `findById returns empty for an unknown id`() {
        assertThat(repository.findById(999_999L)).isEmpty
    }

    @Test
    fun `existsByFilePath reflects presence`() {
        repository.insert("/bookdrop/c.epub", "c.epub", 1L)
        assertThat(repository.existsByFilePath("/bookdrop/c.epub")).isTrue()
        assertThat(repository.existsByFilePath("/bookdrop/missing.epub")).isFalse()
    }

    @Test
    fun `count and countByStatus track status`() {
        val id1 = repository.insert("/bookdrop/1.epub", "1.epub", 1L)
        repository.insert("/bookdrop/2.epub", "2.epub", 1L)
        repository.updateStatus(id1, BookdropFileStatus.FINALIZED)

        assertThat(repository.count()).isEqualTo(2)
        assertThat(repository.countByStatus(BookdropFileStatus.PENDING_REVIEW)).isEqualTo(1)
        assertThat(repository.countByStatus(BookdropFileStatus.FINALIZED)).isEqualTo(1)
    }

    @Test
    fun `findAllByStatus pages only matching rows`() {
        val finalizedId = repository.insert("/bookdrop/x.epub", "x.epub", 1L)
        repository.insert("/bookdrop/y.epub", "y.epub", 1L)
        repository.insert("/bookdrop/z.epub", "z.epub", 1L)
        repository.updateStatus(finalizedId, BookdropFileStatus.FINALIZED)

        val page = repository.findAllByStatus(BookdropFileStatus.PENDING_REVIEW, PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).allMatch { it.status == BookdropFileStatus.PENDING_REVIEW }
        assertThat(page.content.map { it.fileName }).containsExactlyInAnyOrder("y.epub", "z.epub")
    }

    @Test
    fun `findAll paged respects page size`() {
        repository.insert("/bookdrop/1.epub", "1.epub", 1L)
        repository.insert("/bookdrop/2.epub", "2.epub", 1L)
        repository.insert("/bookdrop/3.epub", "3.epub", 1L)

        val page = repository.findAll(PageRequest.of(0, 2))
        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).hasSize(2)
    }

    @Test
    fun `findAllIds and findAllExcludingIdsFlat`() {
        val id1 = repository.insert("/bookdrop/1.epub", "1.epub", 1L)
        val id2 = repository.insert("/bookdrop/2.epub", "2.epub", 1L)
        val id3 = repository.insert("/bookdrop/3.epub", "3.epub", 1L)

        assertThat(repository.findAllIds()).containsExactlyInAnyOrder(id1, id2, id3)
        assertThat(repository.findAllExcludingIdsFlat(listOf(id2)))
            .containsExactlyInAnyOrder(id1, id3)
    }

    @Test
    fun `findAllById returns only requested rows`() {
        val id1 = repository.insert("/bookdrop/1.epub", "1.epub", 1L)
        repository.insert("/bookdrop/2.epub", "2.epub", 1L)
        val id3 = repository.insert("/bookdrop/3.epub", "3.epub", 1L)

        val rows = repository.findAllById(listOf(id1, id3))
        assertThat(rows.map { it.id }).containsExactlyInAnyOrder(id1, id3)
        assertThat(repository.findAllById(emptyList())).isEmpty()
    }

    @Test
    fun `findAllFilePathsIn returns the known subset`() {
        repository.insert("/bookdrop/known1.epub", "known1.epub", 1L)
        repository.insert("/bookdrop/known2.epub", "known2.epub", 1L)

        val result = repository.findAllFilePathsIn(
            listOf("/bookdrop/known1.epub", "/bookdrop/unknown.epub", "/bookdrop/known2.epub")
        )
        assertThat(result).containsExactlyInAnyOrder("/bookdrop/known1.epub", "/bookdrop/known2.epub")
        assertThat(repository.findAllFilePathsIn(emptyList())).isEmpty()
    }

    @Test
    fun `updateOriginalMetadata sets json and bumps updatedAt`() {
        val id = repository.insert("/bookdrop/a.epub", "a.epub", 1L)
        val before = repository.findById(id).get().updatedAt!!

        val updated = repository.updateOriginalMetadata(id, """{"title":"A"}""")
        assertThat(updated.originalMetadata).isEqualTo("""{"title":"A"}""")
        assertThat(updated.updatedAt).isNotNull()
        assertThat(updated.updatedAt!!).isAfterOrEqualTo(before)
        assertThat(updated.status).isEqualTo(BookdropFileStatus.PENDING_REVIEW)
    }

    @Test
    fun `updateFetchedMetadataAndStatus sets both`() {
        val id = repository.insert("/bookdrop/a.epub", "a.epub", 1L)

        val updated = repository.updateFetchedMetadataAndStatus(id, """{"title":"F"}""", BookdropFileStatus.FINALIZED)
        assertThat(updated.fetchedMetadata).isEqualTo("""{"title":"F"}""")
        assertThat(updated.status).isEqualTo(BookdropFileStatus.FINALIZED)
    }

    @Test
    fun `updateStatus changes only the status`() {
        val id = repository.insert("/bookdrop/a.epub", "a.epub", 1L)
        val updated = repository.updateStatus(id, BookdropFileStatus.FINALIZED)
        assertThat(updated.status).isEqualTo(BookdropFileStatus.FINALIZED)
    }

    @Test
    fun `updateFetchedMetadataForIds batch updates only listed ids`() {
        val id1 = repository.insert("/bookdrop/1.epub", "1.epub", 1L)
        val id2 = repository.insert("/bookdrop/2.epub", "2.epub", 1L)
        val id3 = repository.insert("/bookdrop/3.epub", "3.epub", 1L)

        repository.updateFetchedMetadataForIds(mapOf(id1 to "{\"a\":1}", id2 to "{\"b\":2}"))

        assertThat(repository.findById(id1).get().fetchedMetadata).isEqualTo("{\"a\":1}")
        assertThat(repository.findById(id2).get().fetchedMetadata).isEqualTo("{\"b\":2}")
        assertThat(repository.findById(id3).get().fetchedMetadata).isNull()

        // empty map is a no-op
        repository.updateFetchedMetadataForIds(emptyMap())
        assertThat(repository.findById(id3).get().fetchedMetadata).isNull()
    }

    @Test
    fun `deleteAllByFilePathStartingWith removes matching prefix`() {
        repository.insert("/bookdrop/sub/a.epub", "a.epub", 1L)
        repository.insert("/bookdrop/sub/b.epub", "b.epub", 1L)
        repository.insert("/bookdrop/other/c.epub", "c.epub", 1L)

        val deleted = repository.deleteAllByFilePathStartingWith("/bookdrop/sub/")
        assertThat(deleted).isEqualTo(2)
        assertThat(repository.count()).isEqualTo(1)
        assertThat(repository.findAll().single().fileName).isEqualTo("c.epub")
    }

    @Test
    fun `deleteById and deleteAllById remove rows`() {
        val id1 = repository.insert("/bookdrop/1.epub", "1.epub", 1L)
        val id2 = repository.insert("/bookdrop/2.epub", "2.epub", 1L)
        val id3 = repository.insert("/bookdrop/3.epub", "3.epub", 1L)

        repository.deleteById(id1)
        assertThat(repository.findById(id1)).isEmpty

        repository.deleteAllById(listOf(id2, id3))
        assertThat(repository.count()).isZero()

        // empty list is a no-op and does not throw
        repository.deleteAllById(emptyList())
    }
}
