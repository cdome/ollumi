package org.booklore.model.enums

import org.booklore.model.dto.BookLoreUser
import org.booklore.model.dto.request.UserUpdateRequest
import org.booklore.model.entity.UserPermissionsEntity
import java.util.function.BiConsumer
import java.util.function.Predicate

enum class UserPermission(
    val description: String,
    private val dtoGetter: Predicate<BookLoreUser.UserPermissions>,
    private val dtoSetter: BiConsumer<BookLoreUser.UserPermissions, Boolean>,
    private val requestGetter: Predicate<UserUpdateRequest.Permissions>,
    private val entityGetter: Predicate<UserPermissionsEntity>,
    private val entitySetter: BiConsumer<UserPermissionsEntity, Boolean>
) {
    IS_ADMIN(
        "Admin access",
        { it.isAdmin },
        { dto, value -> dto.isAdmin = value },
        { it.isAdmin() },
        { it.isPermissionAdmin },
        { entity, value -> entity.isPermissionAdmin = value }
    ),
    CAN_UPLOAD(
        "Upload books",
        { it.isCanUpload },
        { dto, value -> dto.isCanUpload = value },
        { it.isCanUpload() },
        { it.isPermissionUpload },
        { entity, value -> entity.isPermissionUpload = value }
    ),
    CAN_DOWNLOAD(
        "Download books",
        { it.isCanDownload },
        { dto, value -> dto.isCanDownload = value },
        { it.isCanDownload() },
        { it.isPermissionDownload },
        { entity, value -> entity.isPermissionDownload = value }
    ),
    CAN_EDIT_METADATA(
        "Edit metadata",
        { it.isCanEditMetadata },
        { dto, value -> dto.isCanEditMetadata = value },
        { it.isCanEditMetadata() },
        { it.isPermissionEditMetadata },
        { entity, value -> entity.isPermissionEditMetadata = value }
    ),
    CAN_MANAGE_LIBRARY(
        "Manage library",
        { it.isCanManageLibrary },
        { dto, value -> dto.isCanManageLibrary = value },
        { it.isCanManageLibrary() },
        { it.isPermissionManageLibrary },
        { entity, value -> entity.isPermissionManageLibrary = value }
    ),
    CAN_SYNC_KOREADER(
        "Sync KoReader",
        { it.isCanSyncKoReader },
        { dto, value -> dto.isCanSyncKoReader = value },
        { it.isCanSyncKoReader() },
        { it.isPermissionSyncKoreader },
        { entity, value -> entity.isPermissionSyncKoreader = value }
    ),
    CAN_SYNC_KOBO(
        "Sync Kobo",
        { it.isCanSyncKobo },
        { dto, value -> dto.isCanSyncKobo = value },
        { it.isCanSyncKobo() },
        { it.isPermissionSyncKobo },
        { entity, value -> entity.isPermissionSyncKobo = value }
    ),
    CAN_EMAIL_BOOK(
        "Email books",
        { it.isCanEmailBook },
        { dto, value -> dto.isCanEmailBook = value },
        { it.isCanEmailBook() },
        { it.isPermissionEmailBook },
        { entity, value -> entity.isPermissionEmailBook = value }
    ),
    CAN_DELETE_BOOK(
        "Delete books",
        { it.isCanDeleteBook },
        { dto, value -> dto.isCanDeleteBook = value },
        { it.isCanDeleteBook() },
        { it.isPermissionDeleteBook },
        { entity, value -> entity.isPermissionDeleteBook = value }
    ),
    CAN_ACCESS_OPDS(
        "Access OPDS",
        { it.isCanAccessOpds },
        { dto, value -> dto.isCanAccessOpds = value },
        { it.isCanAccessOpds() },
        { it.isPermissionAccessOpds },
        { entity, value -> entity.isPermissionAccessOpds = value }
    ),
    CAN_MANAGE_METADATA_CONFIG(
        "Manage metadata config",
        { it.isCanManageMetadataConfig },
        { dto, value -> dto.isCanManageMetadataConfig = value },
        { it.isCanManageMetadataConfig() },
        { it.isPermissionManageMetadataConfig },
        { entity, value -> entity.isPermissionManageMetadataConfig = value }
    ),
    CAN_ACCESS_BOOKDROP(
        "Access bookdrop",
        { it.isCanAccessBookdrop },
        { dto, value -> dto.isCanAccessBookdrop = value },
        { it.isCanAccessBookdrop() },
        { it.isPermissionAccessBookdrop },
        { entity, value -> entity.isPermissionAccessBookdrop = value }
    ),
    CAN_ACCESS_LIBRARY_STATS(
        "Access library stats",
        { it.isCanAccessLibraryStats },
        { dto, value -> dto.isCanAccessLibraryStats = value },
        { it.isCanAccessLibraryStats() },
        { it.isPermissionAccessLibraryStats },
        { entity, value -> entity.isPermissionAccessLibraryStats = value }
    ),
    CAN_ACCESS_USER_STATS(
        "Access user stats",
        { it.isCanAccessUserStats },
        { dto, value -> dto.isCanAccessUserStats = value },
        { it.isCanAccessUserStats() },
        { it.isPermissionAccessUserStats },
        { entity, value -> entity.isPermissionAccessUserStats = value }
    ),
    CAN_ACCESS_TASK_MANAGER(
        "Access task manager",
        { it.isCanAccessTaskManager },
        { dto, value -> dto.isCanAccessTaskManager = value },
        { it.isCanAccessTaskManager() },
        { it.isPermissionAccessTaskManager },
        { entity, value -> entity.isPermissionAccessTaskManager = value }
    ),
    CAN_MANAGE_GLOBAL_PREFERENCES(
        "Manage global preferences",
        { it.isCanManageGlobalPreferences },
        { dto, value -> dto.isCanManageGlobalPreferences = value },
        { it.isCanManageGlobalPreferences() },
        { it.isPermissionManageGlobalPreferences },
        { entity, value -> entity.isPermissionManageGlobalPreferences = value }
    ),
    CAN_MANAGE_ICONS(
        "Manage icons",
        { it.isCanManageIcons },
        { dto, value -> dto.isCanManageIcons = value },
        { it.isCanManageIcons() },
        { it.isPermissionManageIcons },
        { entity, value -> entity.isPermissionManageIcons = value }
    ),
    CAN_MANAGE_FONTS(
        "Manage fonts",
        { it.isCanManageFonts },
        { dto, value -> dto.isCanManageFonts = value },
        { it.isCanManageFonts() },
        { it.isPermissionManageFonts },
        { entity, value -> entity.isPermissionManageFonts = value }
    ),
    CAN_BULK_AUTO_FETCH_METADATA(
        "Bulk auto fetch metadata",
        { it.isCanBulkAutoFetchMetadata },
        { dto, value -> dto.isCanBulkAutoFetchMetadata = value },
        { it.isCanBulkAutoFetchMetadata() },
        { it.isPermissionBulkAutoFetchMetadata },
        { entity, value -> entity.isPermissionBulkAutoFetchMetadata = value }
    ),
    CAN_BULK_CUSTOM_FETCH_METADATA(
        "Bulk custom fetch metadata",
        { it.isCanBulkCustomFetchMetadata },
        { dto, value -> dto.isCanBulkCustomFetchMetadata = value },
        { it.isCanBulkCustomFetchMetadata() },
        { it.isPermissionBulkCustomFetchMetadata },
        { entity, value -> entity.isPermissionBulkCustomFetchMetadata = value }
    ),
    CAN_BULK_EDIT_METADATA(
        "Bulk edit metadata",
        { it.isCanBulkEditMetadata },
        { dto, value -> dto.isCanBulkEditMetadata = value },
        { it.isCanBulkEditMetadata() },
        { it.isPermissionBulkEditMetadata },
        { entity, value -> entity.isPermissionBulkEditMetadata = value }
    ),
    CAN_BULK_REGENERATE_COVER(
        "Bulk regenerate cover",
        { it.isCanBulkRegenerateCover },
        { dto, value -> dto.isCanBulkRegenerateCover = value },
        { it.isCanBulkRegenerateCover() },
        { it.isPermissionBulkRegenerateCover },
        { entity, value -> entity.isPermissionBulkRegenerateCover = value }
    ),
    CAN_MOVE_ORGANIZE_FILES(
        "Move/organize files",
        { it.isCanMoveOrganizeFiles },
        { dto, value -> dto.isCanMoveOrganizeFiles = value },
        { it.isCanMoveOrganizeFiles() },
        { it.isPermissionMoveOrganizeFiles },
        { entity, value -> entity.isPermissionMoveOrganizeFiles = value }
    ),
    CAN_BULK_LOCK_UNLOCK_METADATA(
        "Bulk lock/unlock metadata",
        { it.isCanBulkLockUnlockMetadata },
        { dto, value -> dto.isCanBulkLockUnlockMetadata = value },
        { it.isCanBulkLockUnlockMetadata() },
        { it.isPermissionBulkLockUnlockMetadata },
        { entity, value -> entity.isPermissionBulkLockUnlockMetadata = value }
    ),
    CAN_BULK_RESET_BOOKLORE_READ_PROGRESS(
        "Bulk reset Booklore read progress",
        { it.isCanBulkResetBookloreReadProgress },
        { dto, value -> dto.isCanBulkResetBookloreReadProgress = value },
        { it.isCanBulkResetBookloreReadProgress() },
        { it.isPermissionBulkResetBookloreReadProgress },
        { entity, value -> entity.isPermissionBulkResetBookloreReadProgress = value }
    ),
    CAN_BULK_RESET_KOREADER_READ_PROGRESS(
        "Bulk reset KoReader read progress",
        { it.isCanBulkResetKoReaderReadProgress },
        { dto, value -> dto.isCanBulkResetKoReaderReadProgress = value },
        { it.isCanBulkResetKoReaderReadProgress() },
        { it.isPermissionBulkResetKoReaderReadProgress },
        { entity, value -> entity.isPermissionBulkResetKoReaderReadProgress = value }
    ),
    CAN_BULK_RESET_BOOK_READ_STATUS(
        "Bulk reset book read status",
        { it.isCanBulkResetBookReadStatus },
        { dto, value -> dto.isCanBulkResetBookReadStatus = value },
        { it.isCanBulkResetBookReadStatus() },
        { it.isPermissionBulkResetBookReadStatus },
        { entity, value -> entity.isPermissionBulkResetBookReadStatus = value }
    );

    fun isGranted(permissions: BookLoreUser.UserPermissions?): Boolean {
        return permissions != null && dtoGetter.test(permissions)
    }

    fun setInDto(dto: BookLoreUser.UserPermissions?, value: Boolean) {
        if (dto != null) {
            dtoSetter.accept(dto, value)
        }
    }

    fun getFromEntity(entity: UserPermissionsEntity?): Boolean {
        return entity != null && entityGetter.test(entity)
    }

    fun setInEntity(entity: UserPermissionsEntity?, value: Boolean) {
        if (entity != null) {
            entitySetter.accept(entity, value)
        }
    }

    fun getFromRequest(request: UserUpdateRequest.Permissions?): Boolean {
        return request != null && requestGetter.test(request)
    }

    companion object {
        @JvmStatic
        fun copyFromEntityToDto(source: UserPermissionsEntity?, target: BookLoreUser.UserPermissions?) {
            if (source == null || target == null) return
            for (permission in entries) {
                permission.setInDto(target, permission.getFromEntity(source))
            }
        }

        @JvmStatic
        fun copyFromRequestToEntity(source: UserUpdateRequest.Permissions?, target: UserPermissionsEntity?) {
            if (source == null || target == null) return
            for (permission in entries) {
                permission.setInEntity(target, permission.getFromRequest(source))
            }
        }
    }
}
