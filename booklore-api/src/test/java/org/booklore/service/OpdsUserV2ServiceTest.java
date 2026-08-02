package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.service.audit.AuditService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.OpdsUserV2;
import org.booklore.model.dto.request.OpdsUserV2CreateRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.OpdsSortOrder;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqOpdsUserV2Repository;
import org.booklore.service.opds.OpdsUserV2Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpdsUserV2ServiceTest {

    @Mock
    private JooqOpdsUserV2Repository opdsUserV2Repository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private OpdsUserV2Service service;

    @Test
    void getOpdsUsers_returnsDtosFromRepo() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(1L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        List<OpdsUserV2> dtos = List.of(mock(OpdsUserV2.class));
        when(opdsUserV2Repository.findByUserId(1L)).thenReturn(dtos);

        List<OpdsUserV2> result = service.getOpdsUsers();

        assertSame(dtos, result);
        verify(opdsUserV2Repository).findByUserId(1L);
    }

    @Test
    void createOpdsUser_success_insertsWithEncodedPasswordAndReturnsDto() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(1L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        BookLoreUserEntity userEntity = mock(BookLoreUserEntity.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        OpdsUserV2CreateRequest request = mock(OpdsUserV2CreateRequest.class);
        when(request.getUsername()).thenReturn("alice");
        when(request.getPassword()).thenReturn("plaintext");
        when(passwordEncoder.encode("plaintext")).thenReturn("encoded-pass");

        OpdsUserV2 dto = mock(OpdsUserV2.class);
        when(opdsUserV2Repository.insert(1L, "alice", "encoded-pass", OpdsSortOrder.RECENT)).thenReturn(dto);

        OpdsUserV2 result = service.createOpdsUser(request);

        assertSame(dto, result);
        verify(passwordEncoder).encode("plaintext");
        verify(opdsUserV2Repository).insert(1L, "alice", "encoded-pass", OpdsSortOrder.RECENT);
    }

    @Test
    void createOpdsUser_usesProvidedSortOrder() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(1L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(BookLoreUserEntity.class)));

        OpdsUserV2CreateRequest request = mock(OpdsUserV2CreateRequest.class);
        when(request.getUsername()).thenReturn("alice");
        when(request.getPassword()).thenReturn("plaintext");
        when(request.getSortOrder()).thenReturn(OpdsSortOrder.TITLE_ASC);
        when(passwordEncoder.encode("plaintext")).thenReturn("encoded-pass");
        when(opdsUserV2Repository.insert(1L, "alice", "encoded-pass", OpdsSortOrder.TITLE_ASC)).thenReturn(mock(OpdsUserV2.class));

        service.createOpdsUser(request);

        verify(opdsUserV2Repository).insert(1L, "alice", "encoded-pass", OpdsSortOrder.TITLE_ASC);
    }

    @Test
    void createOpdsUser_userNotFound_throwsUsernameNotFoundException() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(2L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        OpdsUserV2CreateRequest request = mock(OpdsUserV2CreateRequest.class);

        assertThrows(UsernameNotFoundException.class, () -> service.createOpdsUser(request));
        verify(opdsUserV2Repository, never()).insert(anyLong(), any(), any(), any());
    }

    @Test
    void deleteOpdsUser_deletesWhenOwner() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(10L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        OpdsUserV2 target = mock(OpdsUserV2.class);
        when(target.getUserId()).thenReturn(10L);
        when(opdsUserV2Repository.findById(100L)).thenReturn(Optional.of(target));

        service.deleteOpdsUser(100L);

        verify(opdsUserV2Repository).deleteById(100L);
    }

    @Test
    void deleteOpdsUser_throwsAccessDeniedWhenNotOwner() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(11L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);

        OpdsUserV2 target = mock(OpdsUserV2.class);
        when(target.getUserId()).thenReturn(9L);
        when(opdsUserV2Repository.findById(200L)).thenReturn(Optional.of(target));

        assertThrows(AccessDeniedException.class, () -> service.deleteOpdsUser(200L));
        verify(opdsUserV2Repository, never()).deleteById(anyLong());
    }

    @Test
    void getOpdsUsers_returnsEmptyListWhenNoUsers() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(5L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);
        when(opdsUserV2Repository.findByUserId(5L)).thenReturn(List.of());

        List<OpdsUserV2> result = service.getOpdsUsers();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(opdsUserV2Repository).findByUserId(5L);
    }

    @Test
    void deleteOpdsUser_userNotFound_throwsRuntimeException() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);
        when(opdsUserV2Repository.findById(300L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deleteOpdsUser(300L));
        assertTrue(ex.getMessage().contains("User not found with ID: 300"));
        verify(opdsUserV2Repository, never()).deleteById(anyLong());
    }

    @Test
    void createOpdsUser_passwordEncoderThrows_propagatesException() {
        BookLoreUser authUser = mock(BookLoreUser.class);
        when(authUser.getId()).thenReturn(6L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);
        when(userRepository.findById(6L)).thenReturn(Optional.of(mock(BookLoreUserEntity.class)));

        OpdsUserV2CreateRequest request = mock(OpdsUserV2CreateRequest.class);
        when(request.getUsername()).thenReturn("bob");
        when(request.getPassword()).thenReturn("plaintext");
        when(passwordEncoder.encode("plaintext")).thenThrow(new IllegalArgumentException("encoding failed"));

        assertThrows(IllegalArgumentException.class, () -> service.createOpdsUser(request));
        verify(opdsUserV2Repository, never()).insert(anyLong(), any(), any(), any());
    }
}
