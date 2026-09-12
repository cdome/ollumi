package org.booklore.service.kobo;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.jooq.dto.KoboUserSettings;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.jooq.JooqBookEntityRepository;
import org.booklore.repository.jooq.JooqKoboUserSettingsRepository;
import org.booklore.repository.jooq.JooqShelfRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KoboAutoShelfServiceTest {

    @Mock
    private JooqKoboUserSettingsRepository koboUserSettingsRepository;

    @Mock
    private JooqShelfRepository jooqShelfRepository;

    @Mock
    private JooqBookEntityRepository jooqBookEntityRepository;

    @Mock
    private KoboCompatibilityService koboCompatibilityService;

    @InjectMocks
    private KoboAutoShelfService koboAutoShelfService;

    private BookEntity testBook;
    private BookLoreUserEntity testUser1;
    private BookLoreUserEntity testUser2;
    private ShelfEntity koboShelf1;
    private ShelfEntity koboShelf2;
    private KoboUserSettings settings1;
    private KoboUserSettings settings2;

    @BeforeEach
    void setUp() {
        testBook = BookEntity.builder()
                .id(1L)
                .build();

        testUser1 = BookLoreUserEntity.builder()
                .id(100L)
                .isDefaultPassword(false).build();

        testUser2 = BookLoreUserEntity.builder()
                .id(200L)
                .isDefaultPassword(false).build();

        koboShelf1 = ShelfEntity.builder()
                .id(10L)
                .name(ShelfType.KOBO.getName())
                .user(testUser1)
                .build();

        koboShelf2 = ShelfEntity.builder()
                .id(20L)
                .name(ShelfType.KOBO.getName())
                .user(testUser2)
                .build();

        settings1 = new KoboUserSettings(1L, 100L, "t1", true, 1f, 99f, true, null, false, false);
        settings2 = new KoboUserSettings(2L, 200L, "t2", true, 1f, 99f, true, null, false, false);
    }

    @Test
    void autoAddBookToKoboShelves_withNullBookId_shouldReturnEarly() {
        koboAutoShelfService.autoAddBookToKoboShelves(null);

        verify(jooqBookEntityRepository, never()).findByIdFull(anyLong());
        verify(koboUserSettingsRepository, never()).findByAutoAddToShelfTrueAndSyncEnabledTrue();
        verify(jooqShelfRepository, never()).findByUserIdInAndName(anyList(), anyString());
        verify(jooqShelfRepository, never()).assignShelfToBook(anyLong(), anyLong());
    }

    @Test
    void autoAddBookToKoboShelves_withNonExistentBook_shouldReturnEarly() {
        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(null);

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(jooqBookEntityRepository).findByIdFull(1L);
        verify(koboUserSettingsRepository, never()).findByAutoAddToShelfTrueAndSyncEnabledTrue();
        verify(jooqShelfRepository, never()).findByUserIdInAndName(anyList(), anyString());
        verify(jooqShelfRepository, never()).assignShelfToBook(anyLong(), anyLong());
    }

    @Test
    void autoAddBookToKoboShelves_withIncompatibleBook_shouldReturnEarly() {
        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(testBook);
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(false);

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(jooqBookEntityRepository).findByIdFull(1L);
        verify(koboCompatibilityService).isBookSupportedForKobo(testBook);
        verify(koboUserSettingsRepository, never()).findByAutoAddToShelfTrueAndSyncEnabledTrue();
        verify(jooqShelfRepository, never()).findByUserIdInAndName(anyList(), anyString());
        verify(jooqShelfRepository, never()).assignShelfToBook(anyLong(), anyLong());
    }

    @Test
    void autoAddBookToKoboShelves_withNoEligibleUsers_shouldReturnEarly() {
        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(testBook);
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(Collections.emptyList());

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(jooqBookEntityRepository).findByIdFull(1L);
        verify(koboCompatibilityService).isBookSupportedForKobo(testBook);
        verify(koboUserSettingsRepository).findByAutoAddToShelfTrueAndSyncEnabledTrue();
        verify(jooqShelfRepository, never()).findByUserIdInAndName(anyList(), anyString());
        verify(jooqShelfRepository, never()).assignShelfToBook(anyLong(), anyLong());
    }

    @Test
    void autoAddBookToKoboShelves_successfully_shouldAddBookToShelves() {
        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(testBook);
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1, settings2));
        when(jooqShelfRepository.findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1, koboShelf2));
        when(jooqShelfRepository.findShelfIdsByBookId(1L)).thenReturn(Collections.emptySet());

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(jooqBookEntityRepository).findByIdFull(1L);
        verify(koboCompatibilityService).isBookSupportedForKobo(testBook);
        verify(koboUserSettingsRepository).findByAutoAddToShelfTrueAndSyncEnabledTrue();
        verify(jooqShelfRepository).findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName());
        verify(jooqShelfRepository).assignShelfToBook(1L, 10L);
        verify(jooqShelfRepository).assignShelfToBook(1L, 20L);
    }

    @Test
    void autoAddBookToKoboShelves_withOneUserMissingShelf_shouldAddOnlyToExistingShelves() {
        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(testBook);
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1, settings2));
        when(jooqShelfRepository.findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1));
        when(jooqShelfRepository.findShelfIdsByBookId(1L)).thenReturn(Collections.emptySet());

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(jooqShelfRepository).assignShelfToBook(1L, 10L);
        verify(jooqShelfRepository, never()).assignShelfToBook(1L, 20L);
    }

    @Test
    void autoAddBookToKoboShelves_withBookAlreadyOnShelf_shouldNotDuplicate() {
        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(testBook);
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1, settings2));
        when(jooqShelfRepository.findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1, koboShelf2));
        when(jooqShelfRepository.findShelfIdsByBookId(1L)).thenReturn(Set.of(10L));

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(jooqShelfRepository, never()).assignShelfToBook(1L, 10L);
        verify(jooqShelfRepository).assignShelfToBook(1L, 20L);
    }

    @Test
    void autoAddBookToKoboShelves_withBookOnAllShelves_shouldNotSave() {
        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(testBook);
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1, settings2));
        when(jooqShelfRepository.findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1, koboShelf2));
        when(jooqShelfRepository.findShelfIdsByBookId(1L)).thenReturn(Set.of(10L, 20L));

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(jooqShelfRepository, never()).assignShelfToBook(anyLong(), anyLong());
    }

    @Test
    void autoAddBookToKoboShelves_withNoKoboShelvesFound_shouldNotSave() {
        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(testBook);
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1, settings2));
        when(jooqShelfRepository.findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName()))
                .thenReturn(Collections.emptyList());
        when(jooqShelfRepository.findShelfIdsByBookId(1L)).thenReturn(Collections.emptySet());

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(jooqShelfRepository).findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName());
        verify(jooqShelfRepository, never()).assignShelfToBook(anyLong(), anyLong());
    }

    @Test
    void autoAddBookToKoboShelves_withSingleUser_shouldAddToSingleShelf() {
        when(jooqBookEntityRepository.findByIdFull(1L)).thenReturn(testBook);
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1));
        when(jooqShelfRepository.findByUserIdInAndName(List.of(100L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1));
        when(jooqShelfRepository.findShelfIdsByBookId(1L)).thenReturn(Collections.emptySet());

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(jooqShelfRepository).assignShelfToBook(1L, 10L);
    }
}
