package org.booklore.model.entity;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPermissionsEntity {

    private Long id;

    private BookLoreUserEntity user;

    private boolean permissionAdmin;

    @Builder.Default
    private boolean permissionUpload = false;

    @Builder.Default
    private boolean permissionDownload = false;

    @Builder.Default
    private boolean permissionEditMetadata = false;

    @Builder.Default
    private boolean permissionManageLibrary = false;

    @Builder.Default
    private boolean permissionEmailBook = false;

    @Builder.Default
    private boolean permissionDeleteBook = false;

    @Builder.Default
    private boolean permissionSyncKoreader = false;

    @Builder.Default
    private boolean permissionAccessOpds = false;

    @Builder.Default
    private boolean permissionSyncKobo = false;

    @Builder.Default
    private boolean permissionManageMetadataConfig = false;

    @Builder.Default
    private boolean permissionAccessBookdrop = false;

    @Builder.Default
    private boolean permissionAccessLibraryStats = false;

    @Builder.Default
    private boolean permissionAccessUserStats = false;

    @Builder.Default
    private boolean permissionAccessTaskManager = false;

    @Builder.Default
    private boolean permissionManageGlobalPreferences = false;

    @Builder.Default
    private boolean permissionManageIcons = false;

    @Builder.Default
    private boolean permissionManageFonts = false;

    @Builder.Default
    private boolean permissionDemoUser = false;

    @Builder.Default
    private boolean permissionBulkAutoFetchMetadata = false;

    @Builder.Default
    private boolean permissionBulkCustomFetchMetadata = false;

    @Builder.Default
    private boolean permissionBulkEditMetadata = false;

    @Builder.Default
    private boolean permissionBulkRegenerateCover = false;

    @Builder.Default
    private boolean permissionMoveOrganizeFiles = false;

    @Builder.Default
    private boolean permissionBulkLockUnlockMetadata = false;

    @Builder.Default
    private boolean permissionBulkResetBookloreReadProgress = false;

    @Builder.Default
    private boolean permissionBulkResetKoReaderReadProgress = false;

    @Builder.Default
    private boolean permissionBulkResetBookReadStatus = false;
}
