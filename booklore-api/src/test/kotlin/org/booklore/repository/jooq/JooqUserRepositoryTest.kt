package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.UserPermissions.USER_PERMISSIONS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.enums.PermissionType
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqUserRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repository: JooqUserRepository

    @Autowired
    private lateinit var dsl: DSLContext

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(USER_PERMISSIONS).execute()
        dsl.deleteFrom(USERS).execute()

        // admin: only ADMIN; uploader: only UPLOAD; librarian: MANAGE_LIBRARY (mapped to
        // permission_manipulate_library); nobody: no permissions.
        insertUser("admin", USER_PERMISSIONS.PERMISSION_ADMIN)
        insertUser("uploader", USER_PERMISSIONS.PERMISSION_UPLOAD)
        insertUser("librarian", USER_PERMISSIONS.PERMISSION_MANIPULATE_LIBRARY)
        insertUser("nobody")
    }

    @Test
    fun `finds users with any of the requested permissions`() {
        val result = repository.findUsernamesWithAnyPermission(
            listOf(PermissionType.ADMIN, PermissionType.UPLOAD)
        )
        assertThat(result).containsExactlyInAnyOrder("admin", "uploader")
    }

    @Test
    fun `resolves the manage-library alias to the manipulate-library column`() {
        val result = repository.findUsernamesWithAnyPermission(listOf(PermissionType.MANAGE_LIBRARY))
        assertThat(result).containsExactly("librarian")
    }

    @Test
    fun `returns empty for an empty permission set`() {
        assertThat(repository.findUsernamesWithAnyPermission(emptyList())).isEmpty()
    }

    @Test
    fun `returns empty when nobody holds the permission`() {
        assertThat(repository.findUsernamesWithAnyPermission(listOf(PermissionType.DEMO_USER))).isEmpty()
    }

    private fun insertUser(username: String, vararg granted: org.jooq.TableField<*, Byte>) {
        val userId = dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID)
            .fetchOne()!!.get(USERS.ID)!!

        val insert = dsl.insertInto(USER_PERMISSIONS).set(USER_PERMISSIONS.USER_ID, userId)
        granted.forEach { insert.set(it, 1.toByte()) }
        insert.execute()
    }
}
