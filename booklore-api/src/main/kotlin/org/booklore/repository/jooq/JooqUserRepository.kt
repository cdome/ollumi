package org.booklore.repository.jooq

import org.booklore.jooq.tables.UserPermissions.USER_PERMISSIONS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.jooq.tables.records.UserPermissionsRecord
import org.booklore.jooq.tables.records.UsersRecord
import org.booklore.model.entity.BookLoreUserEntity
import org.booklore.model.entity.UserPermissionsEntity
import org.booklore.model.enums.PermissionType
import org.booklore.model.enums.ProvisioningMethod
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL.or
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * jOOQ access to the users/user_permissions tables, replacing UserRepository. Every find* method
 * always eager-attaches permissions (a single left join): every real consumer eventually funnels
 * a fetched user through BookLoreUserTransformer.toDTO, which unconditionally reads
 * entity.getPermissions() - so there is no cheaper-but-still-correct narrower hydration available,
 * unlike Book/Shelf/Library where individual call sites could be verified to skip a relation.
 */
@Repository
class JooqUserRepository(private val dsl: DSLContext) {

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

    private fun Byte?.toBoolNonNull() = this == 1.toByte()
    private fun Boolean.toDbByte() = if (this) 1.toByte() else 0.toByte()

    private fun toPermissionsEntity(r: UserPermissionsRecord, user: BookLoreUserEntity): UserPermissionsEntity =
        UserPermissionsEntity.builder()
            .id(r.id)
            .user(user)
            .permissionAdmin(r.permissionAdmin.toBoolNonNull())
            .permissionUpload(r.permissionUpload.toBoolNonNull())
            .permissionDownload(r.permissionDownload.toBoolNonNull())
            .permissionEditMetadata(r.permissionEditMetadata.toBoolNonNull())
            .permissionManageLibrary(r.permissionManipulateLibrary.toBoolNonNull())
            .permissionEmailBook(r.permissionEmailBook.toBoolNonNull())
            .permissionDeleteBook(r.permissionDeleteBook.toBoolNonNull())
            .permissionSyncKoreader(r.permissionSyncKoreader.toBoolNonNull())
            .permissionAccessOpds(r.permissionAccessOpds.toBoolNonNull())
            .permissionSyncKobo(r.permissionSyncKobo.toBoolNonNull())
            .permissionManageMetadataConfig(r.permissionManageMetadataConfig.toBoolNonNull())
            .permissionAccessBookdrop(r.permissionAccessBookdrop.toBoolNonNull())
            .permissionAccessLibraryStats(r.permissionAccessLibraryStats.toBoolNonNull())
            .permissionAccessUserStats(r.permissionAccessUserStats.toBoolNonNull())
            .permissionAccessTaskManager(r.permissionAccessTaskManager.toBoolNonNull())
            .permissionManageGlobalPreferences(r.permissionManageGlobalPreferences.toBoolNonNull())
            .permissionManageIcons(r.permissionManageIcons.toBoolNonNull())
            .permissionManageFonts(r.permissionManageFonts.toBoolNonNull())
            .permissionDemoUser(r.permissionDemoUser.toBoolNonNull())
            .permissionBulkAutoFetchMetadata(r.permissionBulkAutoFetchMetadata.toBoolNonNull())
            .permissionBulkCustomFetchMetadata(r.permissionBulkCustomFetchMetadata.toBoolNonNull())
            .permissionBulkEditMetadata(r.permissionBulkEditMetadata.toBoolNonNull())
            .permissionBulkRegenerateCover(r.permissionBulkRegenerateCover.toBoolNonNull())
            .permissionMoveOrganizeFiles(r.permissionMoveOrganizeFiles.toBoolNonNull())
            .permissionBulkLockUnlockMetadata(r.permissionBulkLockUnlockMetadata.toBoolNonNull())
            .permissionBulkResetBookloreReadProgress(r.permissionBulkResetBookloreReadProgress.toBoolNonNull())
            .permissionBulkResetKoReaderReadProgress(r.permissionBulkResetKoreaderReadProgress.toBoolNonNull())
            .permissionBulkResetBookReadStatus(r.permissionBulkResetBookReadStatus.toBoolNonNull())
            .build()

    private fun toUserEntity(record: Record): BookLoreUserEntity {
        val u = record.into(USERS)
        val user = BookLoreUserEntity.builder()
            .id(u.id)
            .username(u.username)
            .passwordHash(u.passwordHash)
            .isDefaultPassword(u.isDefaultPassword.toBoolNonNull())
            .name(u.name)
            .email(u.email)
            .provisioningMethod(u.provisioningMethod?.let { ProvisioningMethod.valueOf(it) })
            .oidcSubject(u.oidcSubject)
            .oidcIssuer(u.oidcIssuer)
            .avatarUrl(u.avatarUrl)
            .createdAt(u.createdAt)
            .build()
        val p = record.into(USER_PERMISSIONS)
        if (p.id != null) {
            user.permissions = toPermissionsEntity(p, user)
        }
        return user
    }

    private fun baseSelect() = dsl.select(USERS.asterisk(), USER_PERMISSIONS.asterisk())
        .from(USERS)
        .leftJoin(USER_PERMISSIONS).on(USER_PERMISSIONS.USER_ID.eq(USERS.ID))

    fun findById(id: Long): BookLoreUserEntity? =
        baseSelect().where(USERS.ID.eq(id)).fetchOne()?.let(::toUserEntity)

    /** Aliases kept for 1:1 traceability with their JPA call sites - all four eagerly attached
     * permissions under JPA's @EntityGraph too, and every find here does the same single join. */
    fun findByIdWithDetails(id: Long): BookLoreUserEntity? = findById(id)
    fun findByIdWithSettings(id: Long): BookLoreUserEntity? = findById(id)
    fun findByIdWithLibraries(id: Long): BookLoreUserEntity? = findById(id)
    fun findByIdWithPermissions(id: Long): BookLoreUserEntity? = findById(id)

    fun findByUsername(username: String): BookLoreUserEntity? =
        baseSelect().where(USERS.USERNAME.eq(username)).fetchOne()?.let(::toUserEntity)

    fun findByEmail(email: String): BookLoreUserEntity? =
        baseSelect().where(USERS.EMAIL.eq(email)).fetchOne()?.let(::toUserEntity)

    fun findByOidcIssuerAndOidcSubject(oidcIssuer: String, oidcSubject: String): BookLoreUserEntity? =
        baseSelect().where(USERS.OIDC_ISSUER.eq(oidcIssuer)).and(USERS.OIDC_SUBJECT.eq(oidcSubject)).fetchOne()?.let(::toUserEntity)

    fun findAllWithDetails(): List<BookLoreUserEntity> =
        baseSelect().orderBy(USERS.ID).fetch().map(::toUserEntity)

    fun count(): Long = dsl.fetchCount(USERS).toLong()

    fun countByProvisioningMethod(provisioningMethod: ProvisioningMethod): Long =
        dsl.fetchCount(USERS, USERS.PROVISIONING_METHOD.eq(provisioningMethod.name)).toLong()

    fun existsById(id: Long): Boolean = dsl.fetchExists(dsl.selectFrom(USERS).where(USERS.ID.eq(id)))

    fun deleteById(id: Long) {
        dsl.deleteFrom(USERS).where(USERS.ID.eq(id)).execute()
    }

    fun delete(user: BookLoreUserEntity) {
        deleteById(user.id!!)
    }

    /** Mirrors UserRepository.save: insert-or-update the user row based on whether [user] already
     * carries an id, then (matching the entity's cascade=ALL 1:1 permissions association) does the
     * same for [BookLoreUserEntity.getPermissions] if present. created_at is stamped here on insert
     * only, replicating BookLoreUserEntity's old @PrePersist (which never ran on update). */
    fun save(user: BookLoreUserEntity): BookLoreUserEntity {
        val existingId = user.id
        if (existingId == null) {
            val now = LocalDateTime.now()
            val newId = dsl.insertInto(USERS)
                .set(USERS.USERNAME, user.username)
                .set(USERS.PASSWORD_HASH, user.passwordHash)
                .set(USERS.IS_DEFAULT_PASSWORD, user.isDefaultPassword.toDbByte())
                .set(USERS.NAME, user.name)
                .set(USERS.EMAIL, user.email)
                .set(USERS.PROVISIONING_METHOD, user.provisioningMethod?.name)
                .set(USERS.OIDC_SUBJECT, user.oidcSubject)
                .set(USERS.OIDC_ISSUER, user.oidcIssuer)
                .set(USERS.AVATAR_URL, user.avatarUrl)
                .set(USERS.CREATED_AT, now)
                .returning(USERS.ID)
                .fetchOne()!!.id!!
            user.id = newId
            user.createdAt = now
        } else {
            dsl.update(USERS)
                .set(USERS.USERNAME, user.username)
                .set(USERS.PASSWORD_HASH, user.passwordHash)
                .set(USERS.IS_DEFAULT_PASSWORD, user.isDefaultPassword.toDbByte())
                .set(USERS.NAME, user.name)
                .set(USERS.EMAIL, user.email)
                .set(USERS.PROVISIONING_METHOD, user.provisioningMethod?.name)
                .set(USERS.OIDC_SUBJECT, user.oidcSubject)
                .set(USERS.OIDC_ISSUER, user.oidcIssuer)
                .set(USERS.AVATAR_URL, user.avatarUrl)
                .where(USERS.ID.eq(existingId))
                .execute()
        }

        user.permissions?.let { savePermissions(it, user.id!!) }

        return user
    }

    private fun savePermissions(permissions: UserPermissionsEntity, userId: Long) {
        val existingId = permissions.id
        if (existingId == null) {
            val newId = dsl.insertInto(USER_PERMISSIONS)
                .set(USER_PERMISSIONS.USER_ID, userId)
                .set(USER_PERMISSIONS.PERMISSION_ADMIN, permissions.isPermissionAdmin.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_UPLOAD, permissions.isPermissionUpload.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_DOWNLOAD, permissions.isPermissionDownload.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_EDIT_METADATA, permissions.isPermissionEditMetadata.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MANIPULATE_LIBRARY, permissions.isPermissionManageLibrary.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_EMAIL_BOOK, permissions.isPermissionEmailBook.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_DELETE_BOOK, permissions.isPermissionDeleteBook.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_SYNC_KOREADER, permissions.isPermissionSyncKoreader.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_ACCESS_OPDS, permissions.isPermissionAccessOpds.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_SYNC_KOBO, permissions.isPermissionSyncKobo.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MANAGE_METADATA_CONFIG, permissions.isPermissionManageMetadataConfig.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_ACCESS_BOOKDROP, permissions.isPermissionAccessBookdrop.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_ACCESS_LIBRARY_STATS, permissions.isPermissionAccessLibraryStats.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_ACCESS_USER_STATS, permissions.isPermissionAccessUserStats.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_ACCESS_TASK_MANAGER, permissions.isPermissionAccessTaskManager.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MANAGE_GLOBAL_PREFERENCES, permissions.isPermissionManageGlobalPreferences.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MANAGE_ICONS, permissions.isPermissionManageIcons.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MANAGE_FONTS, permissions.isPermissionManageFonts.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_DEMO_USER, permissions.isPermissionDemoUser.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_AUTO_FETCH_METADATA, permissions.isPermissionBulkAutoFetchMetadata.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_CUSTOM_FETCH_METADATA, permissions.isPermissionBulkCustomFetchMetadata.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_EDIT_METADATA, permissions.isPermissionBulkEditMetadata.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_REGENERATE_COVER, permissions.isPermissionBulkRegenerateCover.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MOVE_ORGANIZE_FILES, permissions.isPermissionMoveOrganizeFiles.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_LOCK_UNLOCK_METADATA, permissions.isPermissionBulkLockUnlockMetadata.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_RESET_BOOKLORE_READ_PROGRESS, permissions.isPermissionBulkResetBookloreReadProgress.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_RESET_KOREADER_READ_PROGRESS, permissions.isPermissionBulkResetKoReaderReadProgress.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_RESET_BOOK_READ_STATUS, permissions.isPermissionBulkResetBookReadStatus.toDbByte())
                .returning(USER_PERMISSIONS.ID)
                .fetchOne()!!.id!!
            permissions.id = newId
        } else {
            dsl.update(USER_PERMISSIONS)
                .set(USER_PERMISSIONS.PERMISSION_ADMIN, permissions.isPermissionAdmin.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_UPLOAD, permissions.isPermissionUpload.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_DOWNLOAD, permissions.isPermissionDownload.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_EDIT_METADATA, permissions.isPermissionEditMetadata.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MANIPULATE_LIBRARY, permissions.isPermissionManageLibrary.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_EMAIL_BOOK, permissions.isPermissionEmailBook.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_DELETE_BOOK, permissions.isPermissionDeleteBook.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_SYNC_KOREADER, permissions.isPermissionSyncKoreader.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_ACCESS_OPDS, permissions.isPermissionAccessOpds.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_SYNC_KOBO, permissions.isPermissionSyncKobo.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MANAGE_METADATA_CONFIG, permissions.isPermissionManageMetadataConfig.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_ACCESS_BOOKDROP, permissions.isPermissionAccessBookdrop.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_ACCESS_LIBRARY_STATS, permissions.isPermissionAccessLibraryStats.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_ACCESS_USER_STATS, permissions.isPermissionAccessUserStats.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_ACCESS_TASK_MANAGER, permissions.isPermissionAccessTaskManager.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MANAGE_GLOBAL_PREFERENCES, permissions.isPermissionManageGlobalPreferences.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MANAGE_ICONS, permissions.isPermissionManageIcons.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MANAGE_FONTS, permissions.isPermissionManageFonts.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_DEMO_USER, permissions.isPermissionDemoUser.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_AUTO_FETCH_METADATA, permissions.isPermissionBulkAutoFetchMetadata.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_CUSTOM_FETCH_METADATA, permissions.isPermissionBulkCustomFetchMetadata.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_EDIT_METADATA, permissions.isPermissionBulkEditMetadata.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_REGENERATE_COVER, permissions.isPermissionBulkRegenerateCover.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_MOVE_ORGANIZE_FILES, permissions.isPermissionMoveOrganizeFiles.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_LOCK_UNLOCK_METADATA, permissions.isPermissionBulkLockUnlockMetadata.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_RESET_BOOKLORE_READ_PROGRESS, permissions.isPermissionBulkResetBookloreReadProgress.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_RESET_KOREADER_READ_PROGRESS, permissions.isPermissionBulkResetKoReaderReadProgress.toDbByte())
                .set(USER_PERMISSIONS.PERMISSION_BULK_RESET_BOOK_READ_STATUS, permissions.isPermissionBulkResetBookReadStatus.toDbByte())
                .where(USER_PERMISSIONS.ID.eq(existingId))
                .execute()
        }
    }
}
