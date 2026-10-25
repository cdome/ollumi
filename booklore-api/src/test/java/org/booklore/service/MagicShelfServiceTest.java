package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.service.audit.AuditService;
import org.booklore.model.dto.MagicShelf;
import org.booklore.model.enums.IconType;
import org.booklore.repository.jooq.JooqMagicShelfRepository;
import org.booklore.repository.jooq.dto.MagicShelfRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MagicShelfServiceTest {

    @Mock
    private JooqMagicShelfRepository magicShelfRepository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private MagicShelfService magicShelfService;

    private BookLoreUser user;

    @BeforeEach
    void setUp() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        user = BookLoreUser.builder().id(1L).isDefaultPassword(false).permissions(permissions).build();
    }

    private MagicShelfRow row(Long id, String name, String icon, IconType iconType, String filterJson, boolean isPublic) {
        return new MagicShelfRow(id, 1L, name, icon, iconType, filterJson, isPublic,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void createShelf_withNullIcon_shouldPersistNullIconValues() {
        MagicShelf dto = new MagicShelf();
        dto.setName("Unread Books");
        dto.setIcon(null);
        dto.setIconType(null);
        dto.setFilterJson("{\"status\": \"unread\"}");
        dto.setIsPublic(false);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(magicShelfRepository.existsByUserIdAndName(1L, "Unread Books")).thenReturn(false);
        when(magicShelfRepository.insert(anyLong(), anyString(), any(), any(), anyString(), anyBoolean()))
                .thenReturn(row(1L, "Unread Books", null, null, "{\"status\": \"unread\"}", false));

        MagicShelf result = magicShelfService.createOrUpdateShelf(dto);

        verify(magicShelfRepository).insert(eq(1L), eq("Unread Books"), isNull(), isNull(),
                eq("{\"status\": \"unread\"}"), eq(false));
        assertNull(result.getIcon());
        assertNull(result.getIconType());
        assertEquals("Unread Books", result.getName());
    }

    @Test
    void createShelf_withIcon_shouldPersistIconValues() {
        MagicShelf dto = new MagicShelf();
        dto.setName("Favorites");
        dto.setIcon("star");
        dto.setIconType(IconType.PRIME_NG);
        dto.setFilterJson("{\"rating\": 5}");
        dto.setIsPublic(false);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(magicShelfRepository.existsByUserIdAndName(1L, "Favorites")).thenReturn(false);
        when(magicShelfRepository.insert(anyLong(), anyString(), any(), any(), anyString(), anyBoolean()))
                .thenReturn(row(1L, "Favorites", "star", IconType.PRIME_NG, "{\"rating\": 5}", false));

        MagicShelf result = magicShelfService.createOrUpdateShelf(dto);

        verify(magicShelfRepository).insert(eq(1L), eq("Favorites"), eq("star"), eq(IconType.PRIME_NG),
                eq("{\"rating\": 5}"), eq(false));
        assertNotNull(result);
        assertEquals("star", result.getIcon());
        assertEquals(IconType.PRIME_NG, result.getIconType());
    }

    @Test
    void updateShelf_withNullIcon_shouldClearIconValues() {
        MagicShelfRow existing = row(1L, "Old Shelf", "star", IconType.PRIME_NG, "{\"status\": \"reading\"}", false);

        MagicShelf dto = new MagicShelf();
        dto.setId(1L);
        dto.setName("Updated Shelf");
        dto.setIcon(null);
        dto.setIconType(null);
        dto.setFilterJson("{\"status\": \"updated\"}");
        dto.setIsPublic(false);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(magicShelfRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(magicShelfRepository.update(anyLong(), anyLong(), anyString(), any(), any(), anyString(), anyBoolean()))
                .thenReturn(row(1L, "Updated Shelf", null, null, "{\"status\": \"updated\"}", false));

        magicShelfService.createOrUpdateShelf(dto);

        verify(magicShelfRepository).update(eq(1L), eq(1L), eq("Updated Shelf"), isNull(), isNull(),
                eq("{\"status\": \"updated\"}"), eq(false));
    }

    @Test
    void updateShelf_withIcon_shouldPreserveIconValues() {
        MagicShelfRow existing = row(1L, "Old Shelf", "star", IconType.PRIME_NG, "{\"status\": \"reading\"}", false);

        MagicShelf dto = new MagicShelf();
        dto.setId(1L);
        dto.setName("Updated Shelf");
        dto.setIcon("bookmark");
        dto.setIconType(IconType.CUSTOM_SVG);
        dto.setFilterJson("{\"status\": \"updated\"}");
        dto.setIsPublic(false);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(magicShelfRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(magicShelfRepository.update(anyLong(), anyLong(), anyString(), any(), any(), anyString(), anyBoolean()))
                .thenReturn(row(1L, "Updated Shelf", "bookmark", IconType.CUSTOM_SVG, "{\"status\": \"updated\"}", false));

        magicShelfService.createOrUpdateShelf(dto);

        verify(magicShelfRepository).update(eq(1L), eq(1L), eq("Updated Shelf"), eq("bookmark"),
                eq(IconType.CUSTOM_SVG), eq("{\"status\": \"updated\"}"), eq(false));
    }

    @Test
    void updateShelf_fromIconToNull_shouldAllowRemovingIcon() {
        MagicShelfRow existing = row(1L, "Shelf With Icon", "heart", IconType.PRIME_NG, "{}", false);

        MagicShelf dto = new MagicShelf();
        dto.setId(1L);
        dto.setName("Shelf With Icon");
        dto.setIcon(null);
        dto.setIconType(null);
        dto.setFilterJson("{}");
        dto.setIsPublic(false);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(magicShelfRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(magicShelfRepository.update(anyLong(), anyLong(), anyString(), any(), any(), anyString(), anyBoolean()))
                .thenReturn(row(1L, "Shelf With Icon", null, null, "{}", false));

        MagicShelf result = magicShelfService.createOrUpdateShelf(dto);

        assertNull(result.getIcon());
        assertNull(result.getIconType());
    }
}
