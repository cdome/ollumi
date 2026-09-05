package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.MagicShelf.MAGIC_SHELF
import org.booklore.model.enums.IconType
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqMagicShelfRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqMagicShelfRepository
    @Autowired private lateinit var dsl: DSLContext

    private val userId = 1L
    private val otherUserId = 2L

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(MAGIC_SHELF).execute()
    }

    @Test
    fun `insert round-trips all fields, generates id and sets timestamps`() {
        val row = repository.insert(userId, "Sci-Fi", "pi-book", IconType.PRIME_NG, """{"a":1}""", true)

        assertThat(row.id).isGreaterThan(0)
        assertThat(row.userId).isEqualTo(userId)
        assertThat(row.name).isEqualTo("Sci-Fi")
        assertThat(row.icon).isEqualTo("pi-book")
        assertThat(row.iconType).isEqualTo(IconType.PRIME_NG)
        assertThat(row.filterJson).isEqualTo("""{"a":1}""")
        assertThat(row.isPublic).isTrue()
        assertThat(row.createdAt).isNotNull()
        assertThat(row.updatedAt).isNotNull()

        val reloaded = repository.findById(row.id).orElseThrow()
        assertThat(reloaded.name).isEqualTo("Sci-Fi")
        assertThat(reloaded.icon).isEqualTo("pi-book")
        assertThat(reloaded.iconType).isEqualTo(IconType.PRIME_NG)
        assertThat(reloaded.isPublic).isTrue()
    }

    @Test
    fun `insert persists null icon and icon type`() {
        val row = repository.insert(userId, "No Icon", null, null, "{}", false)

        val reloaded = repository.findById(row.id).orElseThrow()
        assertThat(reloaded.icon).isNull()
        assertThat(reloaded.iconType).isNull()
        assertThat(reloaded.isPublic).isFalse()
    }

    @Test
    fun `findById returns empty for unknown id`() {
        assertThat(repository.findById(999_999L)).isEmpty()
    }

    @Test
    fun `findAllByUserId returns only that user's shelves`() {
        repository.insert(userId, "A", null, null, "{}", false)
        repository.insert(userId, "B", null, null, "{}", true)
        repository.insert(otherUserId, "C", null, null, "{}", false)

        val mine = repository.findAllByUserId(userId)

        assertThat(mine).hasSize(2)
        assertThat(mine.map { it.name }).containsExactlyInAnyOrder("A", "B")
        assertThat(mine).allMatch { it.userId == userId }
    }

    @Test
    fun `findAllPublic returns only public shelves across users`() {
        repository.insert(userId, "PrivateMine", null, null, "{}", false)
        repository.insert(userId, "PublicMine", null, null, "{}", true)
        repository.insert(otherUserId, "PublicOther", null, null, "{}", true)

        val public = repository.findAllPublic()

        assertThat(public.map { it.name }).containsExactlyInAnyOrder("PublicMine", "PublicOther")
        assertThat(public).allMatch { it.isPublic }
    }

    @Test
    fun `existsByUserIdAndName matches only on user and name`() {
        repository.insert(userId, "Favorites", null, null, "{}", false)

        assertThat(repository.existsByUserIdAndName(userId, "Favorites")).isTrue()
        assertThat(repository.existsByUserIdAndName(userId, "Other")).isFalse()
        assertThat(repository.existsByUserIdAndName(otherUserId, "Favorites")).isFalse()
    }

    @Test
    fun `count reflects number of rows`() {
        assertThat(repository.count()).isZero()
        repository.insert(userId, "A", null, null, "{}", false)
        repository.insert(otherUserId, "B", null, null, "{}", true)
        assertThat(repository.count()).isEqualTo(2)
    }

    @Test
    fun `update changes mutable fields and preserves id and user`() {
        val created = repository.insert(userId, "Old", "star", IconType.PRIME_NG, """{"x":1}""", false)

        val updated = repository.update(
            created.id, created.userId, "New", "bookmark", IconType.CUSTOM_SVG, """{"y":2}""", true,
        )

        assertThat(updated.id).isEqualTo(created.id)
        assertThat(updated.userId).isEqualTo(userId)

        val reloaded = repository.findById(created.id).orElseThrow()
        assertThat(reloaded.name).isEqualTo("New")
        assertThat(reloaded.icon).isEqualTo("bookmark")
        assertThat(reloaded.iconType).isEqualTo(IconType.CUSTOM_SVG)
        assertThat(reloaded.filterJson).isEqualTo("""{"y":2}""")
        assertThat(reloaded.isPublic).isTrue()
    }

    @Test
    fun `update can clear icon and icon type to null`() {
        val created = repository.insert(userId, "Old", "star", IconType.PRIME_NG, "{}", false)

        repository.update(created.id, created.userId, "Old", null, null, "{}", false)

        val reloaded = repository.findById(created.id).orElseThrow()
        assertThat(reloaded.icon).isNull()
        assertThat(reloaded.iconType).isNull()
    }

    @Test
    fun `deleteById removes the row`() {
        val created = repository.insert(userId, "Doomed", null, null, "{}", false)

        repository.deleteById(created.id)

        assertThat(repository.findById(created.id)).isEmpty()
    }
}
