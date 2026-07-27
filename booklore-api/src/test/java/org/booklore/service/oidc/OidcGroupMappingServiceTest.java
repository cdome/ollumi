package org.booklore.service.oidc;

import org.booklore.exception.APIException;
import org.booklore.model.dto.OidcGroupMapping;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqOidcGroupMappingRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OidcGroupMappingServiceTest {

    @Mock
    private JooqOidcGroupMappingRepository repository;

    @Mock
    private AuditService auditService;

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OidcGroupMappingService service;

    @Test
    void getAll_returnsRepositoryList() {
        var dtos = List.of(new OidcGroupMapping(1L, "group1", false, List.of(), List.of(), "desc"));
        when(repository.findAll()).thenReturn(dtos);

        var result = service.getAll();

        assertThat(result).isEqualTo(dtos);
        verify(repository).findAll();
    }

    @Test
    void create_insertsAndAudits() {
        var dto = new OidcGroupMapping(99L, "admins", true, List.of("permissionUpload"), List.of(1L), "Admin group");
        var savedDto = new OidcGroupMapping(1L, "admins", true, List.of("permissionUpload"), List.of(1L), "Admin group");
        when(repository.insert(dto)).thenReturn(savedDto);

        var result = service.create(dto);

        assertThat(result).isEqualTo(savedDto);
        verify(repository).insert(dto);
        verify(auditService).log(any(), anyString());
    }

    @Test
    void update_existingMapping_updatesAndAudits() {
        var dto = new OidcGroupMapping(1L, "new-group", true, List.of("permissionUpload"), List.of(2L), "Updated");
        var savedDto = new OidcGroupMapping(1L, "new-group", true, List.of("permissionUpload"), List.of(2L), "Updated");
        when(repository.findById(1L)).thenReturn(new OidcGroupMapping(1L, "old-group", false, List.of(), List.of(), null));
        when(repository.update(1L, dto)).thenReturn(savedDto);

        var result = service.update(1L, dto);

        assertThat(result).isEqualTo(savedDto);
        verify(repository).update(1L, dto);
        verify(auditService).log(any(), anyString());
    }

    @Test
    void update_nonExisting_throwsGenericNotFound() {
        when(repository.findById(999L)).thenReturn(null);
        var dto = new OidcGroupMapping(999L, "group", false, List.of(), List.of(), null);

        assertThatThrownBy(() -> service.update(999L, dto))
                .isInstanceOf(APIException.class);

        verify(repository, never()).update(anyLong(), any());
    }

    @Test
    void delete_existingMapping_deletesAndAudits() {
        when(repository.findById(1L)).thenReturn(new OidcGroupMapping(1L, "group1", false, List.of(), List.of(), null));

        service.delete(1L);

        verify(repository).deleteById(1L);
        verify(auditService).log(any(), anyString());
    }

    @Test
    void delete_nonExisting_throwsGenericNotFound() {
        when(repository.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(APIException.class);

        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void syncUserGroups_nullGroups_doesNothing() {
        var user = mock(BookLoreUserEntity.class);

        service.syncUserGroups(user, null);

        verifyNoInteractions(appSettingService, repository, userRepository);
    }

    @Test
    void syncUserGroups_emptyGroups_doesNothing() {
        var user = mock(BookLoreUserEntity.class);

        service.syncUserGroups(user, Collections.emptyList());

        verifyNoInteractions(appSettingService, repository, userRepository);
    }

    @Test
    void syncUserGroups_disabledMode_doesNothing() {
        var user = mock(BookLoreUserEntity.class);
        var settings = new AppSettings();
        settings.setOidcGroupSyncMode("DISABLED");
        when(appSettingService.getAppSettings()).thenReturn(settings);

        service.syncUserGroups(user, List.of("group1"));

        verifyNoInteractions(repository, userRepository);
    }

    @Test
    void syncUserGroups_nullMode_doesNothing() {
        var user = mock(BookLoreUserEntity.class);
        var settings = new AppSettings();
        settings.setOidcGroupSyncMode(null);
        when(appSettingService.getAppSettings()).thenReturn(settings);

        service.syncUserGroups(user, List.of("group1"));

        verifyNoInteractions(repository, userRepository);
    }

    @Test
    void syncUserGroups_noMatchingMappings_doesNothing() {
        var user = mock(BookLoreUserEntity.class);
        var settings = new AppSettings();
        settings.setOidcGroupSyncMode("ON_LOGIN");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(repository.findByOidcGroupClaimIn(List.of("group1"))).thenReturn(Collections.emptyList());

        service.syncUserGroups(user, List.of("group1"));

        verifyNoInteractions(userRepository);
    }

    @Test
    void syncUserGroups_onLogin_replacesPermissions() {
        var perms = new UserPermissionsEntity();
        perms.setPermissionUpload(true);
        perms.setPermissionDownload(true);
        var user = createMockedUser(perms);

        var mapping = createMapping(false, List.of("permissionUpload", "permissionEditMetadata"), List.of(1L));
        setupSyncMocks("ON_LOGIN", List.of("group1"), List.of(mapping));

        var lib1 = new LibraryEntity();
        lib1.setId(1L);
        when(libraryRepository.findAllById(any())).thenReturn(List.of(lib1));

        service.syncUserGroups(user, List.of("group1"));

        assertThat(perms.isPermissionUpload()).isTrue();
        assertThat(perms.isPermissionDownload()).isFalse();
        assertThat(perms.isPermissionEditMetadata()).isTrue();
        assertThat(perms.isPermissionAdmin()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_onLogin_replacesLibraries() {
        var perms = new UserPermissionsEntity();
        var librariesHolder = new AtomicReference<Set<LibraryEntity>>();
        var existingLib = new LibraryEntity();
        existingLib.setId(10L);
        librariesHolder.set(new HashSet<>(List.of(existingLib)));
        var user = createMockedUserWithLibraries(perms, librariesHolder);

        var mapping = createMapping(false, List.of(), List.of(1L, 2L));
        setupSyncMocks("ON_LOGIN", List.of("group1"), List.of(mapping));

        var lib1 = new LibraryEntity();
        lib1.setId(1L);
        var lib2 = new LibraryEntity();
        lib2.setId(2L);
        when(libraryRepository.findAllById(any())).thenReturn(List.of(lib1, lib2));

        service.syncUserGroups(user, List.of("group1"));

        assertThat(librariesHolder.get()).containsExactlyInAnyOrder(lib1, lib2);
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_onLoginAdditive_onlyAddsPermissions() {
        var perms = new UserPermissionsEntity();
        perms.setPermissionDownload(true);
        var user = createMockedUser(perms);

        var mapping = createMapping(false, List.of("permissionUpload"), List.of(1L));
        setupSyncMocks("ON_LOGIN_ADDITIVE", List.of("group1"), List.of(mapping));

        var lib1 = new LibraryEntity();
        lib1.setId(1L);
        when(libraryRepository.findAllById(any())).thenReturn(List.of(lib1));

        service.syncUserGroups(user, List.of("group1"));

        assertThat(perms.isPermissionUpload()).isTrue();
        assertThat(perms.isPermissionDownload()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_onLoginAdditive_addsLibrariesToExisting() {
        var perms = new UserPermissionsEntity();
        var librariesHolder = new AtomicReference<Set<LibraryEntity>>();
        var existingLib = new LibraryEntity();
        existingLib.setId(10L);
        librariesHolder.set(new HashSet<>(List.of(existingLib)));
        var user = createMockedUserWithLibraries(perms, librariesHolder);

        var mapping = createMapping(false, List.of(), List.of(1L));
        setupSyncMocks("ON_LOGIN_ADDITIVE", List.of("group1"), List.of(mapping));

        var lib1 = new LibraryEntity();
        lib1.setId(1L);
        var lib10 = new LibraryEntity();
        lib10.setId(10L);
        when(libraryRepository.findAllById(any())).thenReturn(List.of(lib1, lib10));

        service.syncUserGroups(user, List.of("group1"));

        assertThat(librariesHolder.get()).hasSize(2);
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_mergesAdminFlagFromMultipleMappings() {
        var perms = new UserPermissionsEntity();
        var user = createMockedUser(perms);

        var mapping1 = createMapping(false, List.of("permissionUpload"), List.of());
        var mapping2 = createMapping(true, List.of("permissionDownload"), List.of());
        setupSyncMocks("ON_LOGIN", List.of("group1", "group2"), List.of(mapping1, mapping2));

        service.syncUserGroups(user, List.of("group1", "group2"));

        assertThat(perms.isPermissionAdmin()).isTrue();
        assertThat(perms.isPermissionUpload()).isTrue();
        assertThat(perms.isPermissionDownload()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_createsPermissionsEntityIfNull() {
        var permissionsHolder = new AtomicReference<UserPermissionsEntity>();
        var user = mock(BookLoreUserEntity.class);
        when(user.getUsername()).thenReturn("testuser");
        when(user.getPermissions()).thenReturn(null).thenAnswer(_ -> permissionsHolder.get());
        doAnswer(invocation -> {
            permissionsHolder.set(invocation.getArgument(0));
            return null;
        }).when(user).setPermissions(any());
        doAnswer(invocation -> {
            permissionsHolder.get().setUser(user);
            return null;
        }).when(user).setLibraries(any());
        lenient().when(user.getLibraries()).thenReturn(null);

        var mapping = createMapping(false, List.of("permissionUpload"), List.of(1L));
        setupSyncMocks("ON_LOGIN", List.of("group1"), List.of(mapping));

        var lib1 = new LibraryEntity();
        lib1.setId(1L);
        when(libraryRepository.findAllById(any())).thenReturn(List.of(lib1));

        service.syncUserGroups(user, List.of("group1"));

        assertThat(permissionsHolder.get()).isNotNull();
        assertThat(permissionsHolder.get().getUser()).isEqualTo(user);
        assertThat(permissionsHolder.get().isPermissionUpload()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void syncUserGroups_unknownMode_doesNothing() {
        var user = mock(BookLoreUserEntity.class);
        var perms = new UserPermissionsEntity();
        lenient().when(user.getPermissions()).thenReturn(perms);

        var mapping = createMapping(false, List.of("permissionUpload"), List.of(1L));
        setupSyncMocks("UNKNOWN_MODE", List.of("group1"), List.of(mapping));

        service.syncUserGroups(user, List.of("group1"));

        verifyNoInteractions(userRepository);
    }

    private BookLoreUserEntity createMockedUser(UserPermissionsEntity perms) {
        var user = mock(BookLoreUserEntity.class);
        lenient().when(user.getUsername()).thenReturn("testuser");
        when(user.getPermissions()).thenReturn(perms);
        lenient().when(user.getLibraries()).thenReturn(null);
        return user;
    }

    private BookLoreUserEntity createMockedUserWithLibraries(UserPermissionsEntity perms,
                                                              AtomicReference<Set<LibraryEntity>> librariesHolder) {
        var user = mock(BookLoreUserEntity.class);
        lenient().when(user.getUsername()).thenReturn("testuser");
        when(user.getPermissions()).thenReturn(perms);
        lenient().when(user.getLibraries()).thenAnswer(_ -> librariesHolder.get());
        doAnswer(invocation -> {
            librariesHolder.set(invocation.getArgument(0));
            return null;
        }).when(user).setLibraries(any());
        return user;
    }

    private OidcGroupMapping createMapping(boolean admin, List<String> permissions, List<Long> libraryIds) {
        return new OidcGroupMapping(1L, "group", admin, permissions, libraryIds, null);
    }

    private void setupSyncMocks(String syncMode, List<String> groups, List<OidcGroupMapping> mappings) {
        var settings = new AppSettings();
        settings.setOidcGroupSyncMode(syncMode);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(repository.findByOidcGroupClaimIn(groups)).thenReturn(mappings);
    }
}
