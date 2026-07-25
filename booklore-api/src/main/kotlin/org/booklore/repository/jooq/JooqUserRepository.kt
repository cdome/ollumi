package org.booklore.repository.jooq

import org.booklore.jooq.tables.UserPermissions.USER_PERMISSIONS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.enums.PermissionType
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL.or
import org.springframework.stereotype.Repository

@Repository
class JooqUserRepository(private val dsl: DSLContext) {

    /**
     * Usernames of users granted at least one of [permissionTypes].
     */
    fun findUsernamesWithAnyPermission(permissionTypes: Collection<PermissionType>): List<String> {
        if (permissionTypes.isEmpty()) return emptyList()

        val conditions = permissionTypes.map { permissionColumn(it).eq(1.toByte()) }

        return dsl.select(USERS.USERNAME)
            .from(USERS)
            .join(USER_PERMISSIONS).on(USER_PERMISSIONS.USER_ID.eq(USERS.ID))
            .where(or(conditions))
            .fetch(USERS.USERNAME)
    }

    private fun permissionColumn(type: PermissionType): Field<Byte> = when (type) {
        PermissionType.ADMIN -> USER_PERMISSIONS.PERMISSION_ADMIN
        PermissionType.UPLOAD -> USER_PERMISSIONS.PERMISSION_UPLOAD
        PermissionType.DOWNLOAD -> USER_PERMISSIONS.PERMISSION_DOWNLOAD
        PermissionType.EDIT_METADATA -> USER_PERMISSIONS.PERMISSION_EDIT_METADATA
        PermissionType.MANAGE_LIBRARY -> USER_PERMISSIONS.PERMISSION_MANIPULATE_LIBRARY
        PermissionType.EMAIL_BOOK -> USER_PERMISSIONS.PERMISSION_EMAIL_BOOK
        PermissionType.DELETE_BOOK -> USER_PERMISSIONS.PERMISSION_DELETE_BOOK
        PermissionType.SYNC_KOREADER -> USER_PERMISSIONS.PERMISSION_SYNC_KOREADER
        PermissionType.SYNC_KOBO -> USER_PERMISSIONS.PERMISSION_SYNC_KOBO
        PermissionType.ACCESS_OPDS -> USER_PERMISSIONS.PERMISSION_ACCESS_OPDS
        PermissionType.MANAGE_METADATA_CONFIG -> USER_PERMISSIONS.PERMISSION_MANAGE_METADATA_CONFIG
        PermissionType.ACCESS_BOOKDROP -> USER_PERMISSIONS.PERMISSION_ACCESS_BOOKDROP
        PermissionType.ACCESS_LIBRARY_STATS -> USER_PERMISSIONS.PERMISSION_ACCESS_LIBRARY_STATS
        PermissionType.ACCESS_USER_STATS -> USER_PERMISSIONS.PERMISSION_ACCESS_USER_STATS
        PermissionType.ACCESS_TASK_MANAGER -> USER_PERMISSIONS.PERMISSION_ACCESS_TASK_MANAGER
        PermissionType.MANAGE_GLOBAL_PREFERENCES -> USER_PERMISSIONS.PERMISSION_MANAGE_GLOBAL_PREFERENCES
        PermissionType.MANAGE_ICONS -> USER_PERMISSIONS.PERMISSION_MANAGE_ICONS
        PermissionType.MANAGE_FONTS -> USER_PERMISSIONS.PERMISSION_MANAGE_FONTS
        PermissionType.DEMO_USER -> USER_PERMISSIONS.PERMISSION_DEMO_USER
    }
}
