package org.booklore.service;

import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.*;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqBookOpdsRepository;
import org.booklore.repository.jooq.JooqBookReadRepository;
import org.booklore.service.library.LibraryService;
import org.booklore.service.opds.OpdsBookService;
import org.booklore.service.restriction.ContentRestrictionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OpdsBookServiceTest {

    @Mock private JooqBookOpdsRepository jooqBookOpdsRepository;
    @Mock private JooqBookReadRepository jooqBookReadRepository;
    @Mock private BookRepository bookRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookLoreUserTransformer bookLoreUserTransformer;
    @Mock private ShelfRepository shelfRepository;
    @Mock private LibraryService libraryService;
    @Mock private ContentRestrictionService contentRestrictionService;

    @InjectMocks private OpdsBookService opdsBookService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        // Feed paths fetch Book DTOs via the jOOQ read model and filter with the DTO restriction variant.
        when(jooqBookReadRepository.findByIds(anyList())).thenReturn(List.of());
        when(contentRestrictionService.applyRestrictionsToDtos(anyList(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // The single-book access check still filters entities.
        when(contentRestrictionService.applyRestrictions(anyList(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    private OpdsUserDetails legacyUserDetails() {
        OpdsUserV2 v2 = OpdsUserV2.builder().userId(999L).username("legacy").build();
        return new OpdsUserDetails(v2);
    }

    private OpdsUserDetails v2UserDetails(Long userId, boolean isAdmin, Set<Long> libraryIds) {
        OpdsUserV2 v2 = OpdsUserV2.builder().userId(userId).username("v2user").build();
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);

        when(userRepository.findByIdWithDetails(userId)).thenReturn(Optional.of(entity));
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(isAdmin);
        when(perms.isCanAccessOpds()).thenReturn(true);

        List<Library> libraries = new ArrayList<>();
        for (Long id : libraryIds) {
            libraries.add(Library.builder().id(id).name("Lib" + id).watch(false).build());
        }
        when(user.getAssignedLibraries()).thenReturn(libraries);

        return new OpdsUserDetails(v2);
    }

    @Test
    void getAccessibleLibraries_returnsEmptyList_whenUserIdIsNull() {
        List<Library> result = opdsBookService.getAccessibleLibraries(null);

        assertThat(result).isEmpty();
    }

    @Test
    void getAccessibleLibraries_returnsAssignedLibraries_forNonAdmin() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(2L, 3L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        List<Library> assigned = List.of(Library.builder().id(2L).watch(false).build());
        when(user.getAssignedLibraries()).thenReturn(assigned);

        List<Library> result = opdsBookService.getAccessibleLibraries(details.getOpdsUserV2().getUserId());

        assertThat(result).isEqualTo(assigned);
    }

    @Test
    void getAccessibleLibraries_returnsAllLibraries_forAdmin() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        List<Library> allLibs = List.of(Library.builder().id(1L).watch(false).build());
        when(libraryService.getAllLibraries()).thenReturn(allLibs);

        List<Library> result = opdsBookService.getAccessibleLibraries(details.getOpdsUserV2().getUserId());

        assertThat(result).isEqualTo(allLibs);
    }

    @Test
    void getBooksPage_legacyUser_delegatesToLegacyMethod() {
        OpdsUserDetails details = legacyUserDetails();
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(999L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));
        when(user.getId()).thenReturn(999L);

        // Mock shelf access
        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(999L);
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findByIdWithUser(2L)).thenReturn(Optional.of(shelf));

        when(jooqBookOpdsRepository.findBookIds(any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByLibraryIds(anySet(), any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByShelfId(anyLong(), any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByShelfIds(anySet(), any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByMetadataSearch(anyString(), any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByMetadataSearchAndLibraryIds(anyString(), anySet(), any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByMetadataSearchAndShelfIds(anyString(), anySet(), any())).thenReturn(Page.empty());

        opdsBookService.getBooksPage(details.getOpdsUserV2().getUserId(), "q", 1L, Set.of(2L), 0, 10);
    }

    @Test
    void getBooksPage_v2User_delegatesToV2Method() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        when(jooqBookOpdsRepository.findBookIds(any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByLibraryIds(anySet(), any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByShelfId(anyLong(), any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByShelfIds(anySet(), any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByMetadataSearch(anyString(), any())).thenReturn(Page.empty());
        when(jooqBookOpdsRepository.findBookIdsByMetadataSearchAndShelfIds(anyString(), anySet(), any())).thenReturn(Page.empty());

        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(true);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(true);
        when(user.getId()).thenReturn(1L);

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(1L);
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findByIdWithUser(anyLong())).thenReturn(Optional.of(shelf));

        opdsBookService.getBooksPage(details.getOpdsUserV2().getUserId(), "q", 1L, Set.of(2L), 0, 10);
    }

    @Test
    void getRecentBooksPage_returnsRecentBooks_forLegacyUser() {
        OpdsUserDetails details = legacyUserDetails();
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        when(userRepository.findByIdWithDetails(999L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(true);

        when(jooqBookOpdsRepository.findBookIds(any())).thenReturn(Page.empty());

        opdsBookService.getRecentBooksPage(details.getOpdsUserV2().getUserId(), 0, 10);
    }

    @Test
    void getRecentBooksPage_appliesBookFilters_forNonAdminV2User() {
        OpdsUserDetails details = v2UserDetails(2L, false, Set.of(1L, 2L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        when(userRepository.findByIdWithDetails(2L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        List<Library> libs = List.of(Library.builder().id(1L).watch(false).build());
        when(user.getAssignedLibraries()).thenReturn(libs);

        Book book = Book.builder().id(1L).shelves(Set.of(Shelf.builder().userId(2L).build())).build();

        when(jooqBookOpdsRepository.findBookIdsByLibraryIds(anySet(), any())).thenReturn(new PageImpl<>(List.of(1L)));
        when(jooqBookReadRepository.findByIds(anyList())).thenReturn(List.of(book));

        Page<Book> result = opdsBookService.getRecentBooksPage(details.getOpdsUserV2().getUserId(), 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getShelves()).allMatch(shelf -> shelf.getUserId().equals(2L));
    }

    @Test
    void getLibraryName_returnsName_whenFound() {
        List<Library> libs = List.of(Library.builder().id(1L).name("Lib1").watch(false).build());
        when(libraryService.getAllLibraries()).thenReturn(libs);

        String name = opdsBookService.getLibraryName(1L);

        assertThat(name).isEqualTo("Lib1");
    }

    @Test
    void getLibraryName_returnsDefault_whenNotFound() {
        when(libraryService.getAllLibraries()).thenReturn(List.of());

        String name = opdsBookService.getLibraryName(99L);

        assertThat(name).isEqualTo("Library Books");
    }

    @Test
    void getShelfName_returnsShelfName_whenFound() {
        ShelfEntity shelf = mock(ShelfEntity.class);
        when(shelf.getName()).thenReturn("Shelf1");
        when(shelfRepository.findById(1L)).thenReturn(Optional.of(shelf));

        String name = opdsBookService.getShelfName(1L);

        assertThat(name).isEqualTo("Shelf1 - Shelf");
    }

    @Test
    void getShelfName_returnsDefault_whenNotFound() {
        when(shelfRepository.findById(1L)).thenReturn(Optional.empty());

        String name = opdsBookService.getShelfName(1L);

        assertThat(name).isEqualTo("Shelf Books");
    }

    @Test
    void getUserShelves_returnsShelves() {
        List<ShelfEntity> shelves = List.of(mock(ShelfEntity.class));
        when(shelfRepository.findByUserId(1L)).thenReturn(shelves);

        List<ShelfEntity> result = opdsBookService.getUserShelves(1L);

        assertThat(result).isEqualTo(shelves);
    }

    @Test
    void getRandomBooks_returnsBooks_whenLibrariesAccessible() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        OpdsBookService spy = Mockito.spy(opdsBookService);
        List<Library> libs = List.of(Library.builder().id(1L).watch(false).build());
        doReturn(libs).when(spy).getAccessibleLibraries(details.getOpdsUserV2().getUserId());

        when(jooqBookOpdsRepository.findRandomBookIdsByLibraryIds(anyList())).thenReturn(List.of(1L, 2L));
        Book book = Book.builder().id(1L).build();
        when(jooqBookReadRepository.findByIds(anyList())).thenReturn(List.of(book));

        List<Book> result = spy.getRandomBooks(details.getOpdsUserV2().getUserId(), 1);

        assertThat(result).hasSize(1);
    }

    @Test
    void getRandomBooks_returnsEmpty_whenNoLibraries() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of());
        OpdsBookService spy = Mockito.spy(opdsBookService);
        doReturn(List.of()).when(spy).getAccessibleLibraries(details.getOpdsUserV2().getUserId());

        List<Book> result = spy.getRandomBooks(details.getOpdsUserV2().getUserId(), 1);

        assertThat(result).isEmpty();
    }

    @Test
    void getBooksPageForV2User_throwsForbidden_whenNoPermission() {
        OpdsUserV2 v2 = OpdsUserV2.builder().userId(1L).build();
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(false);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() ->
                opdsBookService.getBooksPage(1L, null, null, null, 0, 10)
        ).hasMessageContaining("You are not allowed to access this resource");
    }

    @Test
    void getBooksPage_withSingleShelfId_returnsShelfBooks() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(1L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(perms.isCanAccessOpds()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(1L);
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findByIdWithUser(10L)).thenReturn(Optional.of(shelf));

        Book book = Book.builder().id(1L).build();

        when(jooqBookOpdsRepository.findBookIdsByShelfIds(eq(Set.of(10L)), any())).thenReturn(new PageImpl<>(List.of(1L)));
        when(jooqBookReadRepository.findByIds(eq(List.of(1L)))).thenReturn(List.of(book));

        Page<Book> result = opdsBookService.getBooksPage(1L, null, null, Set.of(10L), 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(jooqBookOpdsRepository).findBookIdsByShelfIds(eq(Set.of(10L)), any());
    }

    @Test
    void getBooksPage_withMultipleShelfIds_returnsShelfBooks() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(1L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(perms.isCanAccessOpds()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        ShelfEntity shelf1 = mock(ShelfEntity.class);
        ShelfEntity shelf2 = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(1L);
        when(shelf1.getUser()).thenReturn(shelfUser);
        when(shelf2.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findByIdWithUser(10L)).thenReturn(Optional.of(shelf1));
        when(shelfRepository.findByIdWithUser(20L)).thenReturn(Optional.of(shelf2));

        Book book1 = Book.builder().id(1L).build();
        Book book2 = Book.builder().id(2L).build();

        when(jooqBookOpdsRepository.findBookIdsByShelfIds(eq(Set.of(10L, 20L)), any())).thenReturn(new PageImpl<>(List.of(1L, 2L)));
        when(jooqBookReadRepository.findByIds(eq(List.of(1L, 2L)))).thenReturn(List.of(book1, book2));

        Page<Book> result = opdsBookService.getBooksPage(1L, null, null, Set.of(10L, 20L), 0, 10);

        assertThat(result.getContent()).hasSize(2);
        verify(jooqBookOpdsRepository).findBookIdsByShelfIds(eq(Set.of(10L, 20L)), any());
    }

    @Test
    void getBooksPage_withShelfIdAndQuery_searchesInShelf() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(1L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(perms.isCanAccessOpds()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(1L);
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findByIdWithUser(10L)).thenReturn(Optional.of(shelf));

        Book book = Book.builder().id(1L).build();

        when(jooqBookOpdsRepository.findBookIdsByMetadataSearchAndShelfIds(eq("test"), eq(Set.of(10L)), any())).thenReturn(new PageImpl<>(List.of(1L)));
        when(jooqBookReadRepository.findByIds(eq(List.of(1L)))).thenReturn(List.of(book));

        Page<Book> result = opdsBookService.getBooksPage(1L, "test", null, Set.of(10L), 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(jooqBookOpdsRepository).findBookIdsByMetadataSearchAndShelfIds(eq("test"), eq(Set.of(10L)), any());
    }

    @Test
    void getBooksPage_withShelfId_throwsForbidden_whenNotOwner() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(1L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(perms.isCanAccessOpds()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(999L); // Different user
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findByIdWithUser(10L)).thenReturn(Optional.of(shelf));

        assertThatThrownBy(() ->
                opdsBookService.getBooksPage(1L, null, null, Set.of(10L), 0, 10)
        ).hasMessageContaining("You are not allowed to access this shelf");
    }

    @Test
    void getBooksPage_withShelfId_allowsAdmin_evenIfNotOwner() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(true);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(true);
        when(perms.isCanAccessOpds()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(999L); // Different user, but admin
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findByIdWithUser(10L)).thenReturn(Optional.of(shelf));

        when(jooqBookOpdsRepository.findBookIdsByShelfIds(eq(Set.of(10L)), any())).thenReturn(Page.empty());

        Page<Book> result = opdsBookService.getBooksPage(1L, null, null, Set.of(10L), 0, 10);

        assertThat(result).isNotNull();
        verify(jooqBookOpdsRepository).findBookIdsByShelfIds(eq(Set.of(10L)), any());
    }

    // ==================== validateBookContentAccess ====================

    @Test
    void validateBookContentAccess_throwsForbidden_whenUserIdIsNull() {
        assertThatThrownBy(() ->
                opdsBookService.validateBookContentAccess(1L, null)
        ).hasMessageContaining("Authentication required");
    }

    @Test
    void validateBookContentAccess_allowsAdmin() {
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(true);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        opdsBookService.validateBookContentAccess(99L, 1L);

        verify(bookRepository, never()).findById(anyLong());
        verify(contentRestrictionService, never()).applyRestrictions(anyList(), eq(1L));
    }

    @Test
    void validateBookContentAccess_throwsForbidden_whenNoLibraryAccess() {
        BookLoreUserEntity userEntity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(userEntity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(2L)).thenReturn(Optional.of(userEntity));

        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(userEntity)).thenReturn(user);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(5L).watch(false).build()));

        BookEntity book = mock(BookEntity.class);
        LibraryEntity library = mock(LibraryEntity.class);
        when(library.getId()).thenReturn(99L);
        when(book.getLibrary()).thenReturn(library);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() ->
                opdsBookService.validateBookContentAccess(1L, 2L)
        ).hasMessageContaining("You are not authorized to access this book.");
    }

    @Test
    void validateBookContentAccess_throwsForbidden_whenContentRestricted() {
        BookLoreUserEntity userEntity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(userEntity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(2L)).thenReturn(Optional.of(userEntity));

        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(userEntity)).thenReturn(user);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(5L).watch(false).build()));

        BookEntity book = mock(BookEntity.class);
        LibraryEntity library = mock(LibraryEntity.class);
        when(library.getId()).thenReturn(5L);
        when(book.getLibrary()).thenReturn(library);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        when(contentRestrictionService.applyRestrictions(eq(List.of(book)), eq(2L)))
                .thenReturn(List.of());

        assertThatThrownBy(() ->
                opdsBookService.validateBookContentAccess(1L, 2L)
        ).hasMessageContaining("You are not authorized to access this book.");
    }

    @Test
    void validateBookContentAccess_allowsAccess_whenBookPassesRestrictions() {
        BookLoreUserEntity userEntity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(userEntity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(2L)).thenReturn(Optional.of(userEntity));

        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(userEntity)).thenReturn(user);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(5L).watch(false).build()));

        BookEntity book = mock(BookEntity.class);
        LibraryEntity library = mock(LibraryEntity.class);
        when(library.getId()).thenReturn(5L);
        when(book.getLibrary()).thenReturn(library);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        when(contentRestrictionService.applyRestrictions(eq(List.of(book)), eq(2L)))
                .thenReturn(List.of(book));

        opdsBookService.validateBookContentAccess(1L, 2L);

        verify(contentRestrictionService).applyRestrictions(eq(List.of(book)), eq(2L));
    }

    // ==================== Content restriction filtering in feeds ====================

    @Test
    void getBooksPage_appliesContentRestrictions_forNonAdmin() {
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        Book allowedBook = Book.builder().id(1L).build();
        Book restrictedBook = Book.builder().id(2L).build();

        when(jooqBookOpdsRepository.findBookIdsByLibraryIds(anySet(), any()))
                .thenReturn(new PageImpl<>(List.of(1L, 2L)));
        when(jooqBookReadRepository.findByIds(anyList()))
                .thenReturn(List.of(allowedBook, restrictedBook));

        when(contentRestrictionService.applyRestrictionsToDtos(anyList(), eq(1L)))
                .thenReturn(List.of(allowedBook));

        Page<Book> result = opdsBookService.getBooksPage(1L, null, null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(1L);
        verify(contentRestrictionService).applyRestrictionsToDtos(anyList(), eq(1L));
    }

    @Test
    void getBooksPage_skipsContentRestrictions_forAdmin() {
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(true);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(true);
        when(user.getId()).thenReturn(1L);

        when(jooqBookOpdsRepository.findBookIds(any())).thenReturn(Page.empty());

        opdsBookService.getBooksPage(1L, null, null, null, 0, 10);

        verify(contentRestrictionService, never()).applyRestrictionsToDtos(anyList(), eq(1L));
    }

    @Test
    void getRecentBooksPage_appliesContentRestrictions_forNonAdmin() {
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        when(userRepository.findByIdWithDetails(2L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        Book book = Book.builder().id(1L).build();

        when(jooqBookOpdsRepository.findBookIdsByLibraryIds(anySet(), any()))
                .thenReturn(new PageImpl<>(List.of(1L)));
        when(jooqBookReadRepository.findByIds(anyList())).thenReturn(List.of(book));

        opdsBookService.getRecentBooksPage(2L, 0, 10);

        verify(contentRestrictionService).applyRestrictionsToDtos(anyList(), eq(2L));
    }

    @Test
    void getRecentBooksPage_skipsContentRestrictions_forAdmin() {
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        when(userRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(true);

        when(jooqBookOpdsRepository.findBookIds(any())).thenReturn(Page.empty());

        opdsBookService.getRecentBooksPage(1L, 0, 10);

        verify(contentRestrictionService, never()).applyRestrictionsToDtos(anyList(), eq(1L));
    }

    @Test
    void getRandomBooks_appliesContentRestrictions() {
        OpdsBookService spy = Mockito.spy(opdsBookService);
        doReturn(List.of(Library.builder().id(1L).watch(false).build()))
                .when(spy).getAccessibleLibraries(2L);

        Book allowedBook = Book.builder().id(1L).build();
        Book restrictedBook = Book.builder().id(2L).build();

        when(jooqBookOpdsRepository.findRandomBookIdsByLibraryIds(anyList())).thenReturn(List.of(1L, 2L));
        when(jooqBookReadRepository.findByIds(anyList()))
                .thenReturn(List.of(allowedBook, restrictedBook));

        when(contentRestrictionService.applyRestrictionsToDtos(anyList(), eq(2L)))
                .thenReturn(List.of(allowedBook));

        List<Book> result = spy.getRandomBooks(2L, 5);

        assertThat(result).hasSize(1);
        verify(contentRestrictionService).applyRestrictionsToDtos(anyList(), eq(2L));
    }

    @Test
    void getBooksPage_contentRestrictions_filtersFromPage() {
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findByIdWithDetails(3L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(user.getId()).thenReturn(3L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        Book book1 = Book.builder().id(1L).build();
        Book book2 = Book.builder().id(2L).build();
        Book book3 = Book.builder().id(3L).build();

        when(jooqBookOpdsRepository.findBookIdsByLibraryIds(anySet(), any()))
                .thenReturn(new PageImpl<>(List.of(1L, 2L, 3L)));
        when(jooqBookReadRepository.findByIds(anyList()))
                .thenReturn(List.of(book1, book2, book3));

        when(contentRestrictionService.applyRestrictionsToDtos(anyList(), eq(3L)))
                .thenReturn(List.of(book1, book3));

        Page<Book> result = opdsBookService.getBooksPage(3L, null, null, null, 0, 10);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Book::getId).containsExactly(1L, 3L);
    }

}
