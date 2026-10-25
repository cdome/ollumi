package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.LibraryPath.LIBRARY_PATH
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqLibraryPathRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqLibraryPathRepository
    @Autowired private lateinit var dsl: DSLContext

    private var libA = 0L
    private var libB = 0L
    private var pathA1 = 0L
    private var pathA2 = 0L
    private var pathB1 = 0L

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(LIBRARY_PATH).execute()
        dsl.deleteFrom(LIBRARY).execute()
        libA = newLibrary("A")
        libB = newLibrary("B")
        pathA1 = newPath(libA, "/books/a1")
        pathA2 = newPath(libA, "/books/a2")
        pathB1 = newPath(libB, "/books/b1")
    }

    private fun newLibrary(name: String): Long =
        dsl.insertInto(LIBRARY).set(LIBRARY.NAME, name)
            .returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!

    private fun newPath(libraryId: Long, path: String): Long =
        dsl.insertInto(LIBRARY_PATH).set(LIBRARY_PATH.LIBRARY_ID, libraryId).set(LIBRARY_PATH.PATH, path)
            .returningResult(LIBRARY_PATH.ID).fetchOne()!!.get(LIBRARY_PATH.ID)!!

    @Test
    fun `findPathByIdAndLibraryId returns the path only for the owning library`() {
        assertThat(repository.findPathByIdAndLibraryId(pathA1, libA)).isEqualTo("/books/a1")
        // the guard that FileUploadService relies on: a path from another library must not resolve
        assertThat(repository.findPathByIdAndLibraryId(pathB1, libA)).isNull()
        assertThat(repository.findPathByIdAndLibraryId(999_999L, libA)).isNull()
    }

    @Test
    fun `findPathsByLibraryId returns that library's paths ordered by id`() {
        val paths = repository.findPathsByLibraryId(libA)
        assertThat(paths.map { it.id }).containsExactly(pathA1, pathA2)
        assertThat(paths.map { it.path }).containsExactly("/books/a1", "/books/a2")
        assertThat(repository.findPathsByLibraryId(999_999L)).isEmpty()
    }

    @Test
    fun `findPathsByLibraryIds groups by library`() {
        val grouped = repository.findPathsByLibraryIds(listOf(libA, libB))
        assertThat(grouped.keys).containsExactlyInAnyOrder(libA, libB)
        assertThat(grouped[libA]!!.map { it.path }).containsExactly("/books/a1", "/books/a2")
        assertThat(grouped[libB]!!.map { it.path }).containsExactly("/books/b1")
        assertThat(repository.findPathsByLibraryIds(emptyList())).isEmpty()
    }

    @Test
    fun `countPathsByLibraryId counts only that library`() {
        assertThat(repository.countPathsByLibraryId(libA)).isEqualTo(2)
        assertThat(repository.countPathsByLibraryId(libB)).isEqualTo(1)
        assertThat(repository.countPathsByLibraryId(999_999L)).isEqualTo(0)
    }
}
