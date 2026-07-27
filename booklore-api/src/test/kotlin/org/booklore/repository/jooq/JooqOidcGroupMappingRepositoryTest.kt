package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.OidcGroupMapping.OIDC_GROUP_MAPPING
import org.booklore.model.dto.OidcGroupMapping
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqOidcGroupMappingRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqOidcGroupMappingRepository
    @Autowired private lateinit var dsl: DSLContext

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(OIDC_GROUP_MAPPING).execute()
    }

    @Test
    fun `insert serializes lists to json and round-trips via findById`() {
        val saved = repository.insert(
            OidcGroupMapping(null, "admins", true, listOf("permissionUpload", "permissionDownload"), listOf(1L, 2L), "Admin group")
        )

        assertThat(saved.id()).isNotNull()
        assertThat(saved.oidcGroupClaim()).isEqualTo("admins")
        assertThat(saved.isAdmin).isTrue()
        assertThat(saved.permissions()).containsExactly("permissionUpload", "permissionDownload")
        assertThat(saved.libraryIds()).containsExactly(1L, 2L)
        assertThat(saved.description()).isEqualTo("Admin group")

        // stored as JSON strings
        val rawPerms = dsl.select(OIDC_GROUP_MAPPING.PERMISSIONS).from(OIDC_GROUP_MAPPING).fetchOne(OIDC_GROUP_MAPPING.PERMISSIONS)
        assertThat(rawPerms).contains("permissionUpload").startsWith("[")
    }

    @Test
    fun `empty lists are stored as empty json arrays and read back empty`() {
        val saved = repository.insert(OidcGroupMapping(null, "empty", false, emptyList(), emptyList(), null))

        val found = repository.findById(saved.id())!!
        assertThat(found.permissions()).isEmpty()
        assertThat(found.libraryIds()).isEmpty()
        assertThat(found.isAdmin).isFalse()
    }

    @Test
    fun `update overwrites all fields`() {
        val saved = repository.insert(OidcGroupMapping(null, "old", false, listOf("permissionUpload"), listOf(1L), "old"))

        val updated = repository.update(saved.id(),
            OidcGroupMapping(saved.id(), "new", true, listOf("permissionDownload"), listOf(3L), "new"))

        assertThat(updated.oidcGroupClaim()).isEqualTo("new")
        assertThat(updated.isAdmin).isTrue()
        assertThat(updated.permissions()).containsExactly("permissionDownload")
        assertThat(updated.libraryIds()).containsExactly(3L)
        assertThat(updated.description()).isEqualTo("new")
        assertThat(dsl.fetchCount(OIDC_GROUP_MAPPING)).isEqualTo(1)
    }

    @Test
    fun `findByOidcGroupClaimIn returns only matching claims`() {
        repository.insert(OidcGroupMapping(null, "g1", false, emptyList(), emptyList(), null))
        repository.insert(OidcGroupMapping(null, "g2", false, emptyList(), emptyList(), null))
        repository.insert(OidcGroupMapping(null, "g3", false, emptyList(), emptyList(), null))

        val result = repository.findByOidcGroupClaimIn(listOf("g1", "g3"))

        assertThat(result).extracting("oidcGroupClaim").containsExactlyInAnyOrder("g1", "g3")
    }

    @Test
    fun `findAll ordered by id, findById null when absent, delete removes`() {
        val a = repository.insert(OidcGroupMapping(null, "a", false, emptyList(), emptyList(), null))
        val b = repository.insert(OidcGroupMapping(null, "b", false, emptyList(), emptyList(), null))

        assertThat(repository.findAll().map { it.oidcGroupClaim() }).containsExactly("a", "b")

        repository.deleteById(a.id())
        assertThat(repository.findById(a.id())).isNull()
        assertThat(repository.findAll().map { it.oidcGroupClaim() }).containsExactly("b")
        assertThat(repository.findById(b.id())).isNotNull()
    }
}
