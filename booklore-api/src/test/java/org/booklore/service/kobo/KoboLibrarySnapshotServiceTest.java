package org.booklore.service.kobo;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookLoreUser.UserPermissions;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.jooq.JooqKoboDeletedBookProgressRepository;
import org.booklore.repository.jooq.JooqKoboLibrarySnapshotRepository;
import org.booklore.repository.jooq.JooqKoboSnapshotBookRepository;
import org.booklore.repository.jooq.dto.KoboLibrarySnapshot;
import org.booklore.repository.jooq.dto.KoboSnapshotBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoboLibrarySnapshotServiceTest {

    @Mock
    private JooqKoboLibrarySnapshotRepository koboLibrarySnapshotRepository;

    @Mock
    private JooqKoboSnapshotBookRepository koboSnapshotBookRepository;

    @Mock
    private ShelfRepository shelfRepository;

    @Mock
    private JooqKoboDeletedBookProgressRepository koboDeletedBookProgressRepository;

    @Mock
    private KoboCompatibilityService koboCompatibilityService;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private KoboLibrarySnapshotService service;

    @Captor
    private ArgumentCaptor<List<KoboSnapshotBook>> booksCaptor;

    private BookLoreUserEntity owner;
    private BookLoreUserEntity otherUser;
    private BookEntity ownersBook;
    private BookEntity otherUsersBook;

    @BeforeEach
    void setUp() {
        owner = BookLoreUserEntity.builder().id(1L).isDefaultPassword(false).build();
        otherUser = BookLoreUserEntity.builder().id(2L).isDefaultPassword(false).build();

        LibraryEntity ownersLibrary = LibraryEntity.builder()
                .users(List.of(owner))
                .build();

        LibraryEntity othersLibrary = LibraryEntity.builder()
                .users(List.of(otherUser))
                .build();

        ownersBook = BookEntity.builder()
                .id(101L)
                .library(ownersLibrary)
                .build();

        BookFileEntity ownersPrimaryFile = new BookFileEntity();
        ownersPrimaryFile.setBook(ownersBook);
        ownersBook.setBookFiles(List.of(ownersPrimaryFile));

        otherUsersBook = BookEntity.builder()
                .id(202L)
                .library(othersLibrary)
                .build();

        BookFileEntity otherPrimaryFile = new BookFileEntity();
        otherPrimaryFile.setBook(otherUsersBook);
        otherUsersBook.setBookFiles(List.of(otherPrimaryFile));

        UserPermissions userPermissions = new UserPermissions();
        userPermissions.setAdmin(false);

        BookLoreUser mockUser = BookLoreUser.builder()
                .id(owner.getId())
                .permissions(userPermissions)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(mockUser);

        // create() always builds the child books and persists the aggregate via insert(...).
        // Echo back a snapshot record built from the captured arguments so callers see the userId/id/date.
        when(koboLibrarySnapshotRepository.insert(anyString(), anyLong(), any(LocalDateTime.class), booksCaptor.capture()))
                .thenAnswer(invocation -> new KoboLibrarySnapshot(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)));
    }

    @Test
    void create_shouldIncludeOnlyBooksOwnedBySnapshotUser() {
        ShelfEntity shelf = ShelfEntity.builder()
                .name(ShelfType.KOBO.getName())
                .bookEntities(Set.of(ownersBook, otherUsersBook))
                .build();

        when(shelfRepository.findByUserIdAndName(eq(owner.getId()), eq(ShelfType.KOBO.getName())))
                .thenReturn(Optional.of(shelf));

        when(koboCompatibilityService.isBookSupportedForKobo(any())).thenReturn(true);

        KoboLibrarySnapshot created = service.create(owner.getId());

        assertThat(created.getUserId()).isEqualTo(owner.getId());

        // The books built for the insert must be exactly the snapshot user's owned books.
        assertThat(booksCaptor.getValue())
                .extracting(KoboSnapshotBook::getBookId)
                .containsExactly(ownersBook.getId());
        assertThat(booksCaptor.getValue())
                .allMatch(book -> !book.getSynced());
    }

    @Test
    void create_shouldSkipOwnedBooksThatAreIncompatibleWithKobo() {
        ShelfEntity shelf = ShelfEntity.builder()
                .name(ShelfType.KOBO.getName())
                .bookEntities(Set.of(ownersBook))
                .build();

        when(shelfRepository.findByUserIdAndName(eq(owner.getId()), eq(ShelfType.KOBO.getName())))
                .thenReturn(Optional.of(shelf));

        when(koboCompatibilityService.isBookSupportedForKobo(ownersBook)).thenReturn(false);

        service.create(owner.getId());

        assertThat(booksCaptor.getValue()).isEmpty();
    }
}
