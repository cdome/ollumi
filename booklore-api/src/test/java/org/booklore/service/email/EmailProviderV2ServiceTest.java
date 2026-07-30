package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.EmailProviderV2;
import org.booklore.model.dto.request.CreateEmailProviderRequest;
import org.booklore.repository.UserEmailProviderPreferenceRepository;
import org.booklore.repository.jooq.JooqEmailProviderV2Repository;
import org.booklore.repository.jooq.dto.EmailProviderV2Row;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailProviderV2ServiceTest {

    @Mock
    private JooqEmailProviderV2Repository repository;

    @Mock
    private UserEmailProviderPreferenceRepository preferenceRepository;

    @Mock
    private AuthenticationService authService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private EmailProviderV2Service emailProviderV2Service;

    private BookLoreUser adminUser;
    private BookLoreUser regularUser;
    private EmailProviderV2Row savedRow;

    @BeforeEach
    void setUp() {
        BookLoreUser.UserPermissions adminPerms = new BookLoreUser.UserPermissions();
        adminPerms.setAdmin(true);
        adminUser = BookLoreUser.builder().id(1L).username("admin").permissions(adminPerms).build();

        BookLoreUser.UserPermissions regularPerms = new BookLoreUser.UserPermissions();
        regularPerms.setAdmin(false);
        regularUser = BookLoreUser.builder().id(2L).username("user").permissions(regularPerms).build();

        // Row returned by insert/update stubs. password is present on the row (the service must
        // drop it when mapping to the password-less EmailProviderV2 DTO).
        savedRow = row(10L, 1L, false);
    }

    private EmailProviderV2Row row(long id, long userId, boolean shared) {
        return new EmailProviderV2Row(
                id,
                userId,
                "Test",
                "smtp.test.com",
                587,
                "user@test.com",
                "secret",
                null,
                false,
                false,
                false,
                shared);
    }

    @Test
    void createEmailProvider_nullShared_setsSharedFalse() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Test")
                .host("smtp.test.com")
                .username("user@test.com")
                .password("secret")
                .port(587)
                .shared(null)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.insert(any(EmailProviderV2Row.class))).thenReturn(savedRow);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());

        EmailProviderV2 result = emailProviderV2Service.createEmailProvider(request);

        ArgumentCaptor<EmailProviderV2Row> captor = ArgumentCaptor.forClass(EmailProviderV2Row.class);
        verify(repository).insert(captor.capture());
        assertFalse(captor.getValue().getShared());
        // returned DTO is mapped from the saved row (password dropped, not present on the DTO type)
        assertEquals(10L, result.getId());
        assertEquals("smtp.test.com", result.getHost());
        assertFalse(result.getDefaultProvider());
    }

    @Test
    void createEmailProvider_sharedFalse_setsSharedFalse() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Test")
                .host("smtp.test.com")
                .username("user@test.com")
                .password("secret")
                .port(587)
                .shared(false)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.insert(any(EmailProviderV2Row.class))).thenReturn(savedRow);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());

        emailProviderV2Service.createEmailProvider(request);

        ArgumentCaptor<EmailProviderV2Row> captor = ArgumentCaptor.forClass(EmailProviderV2Row.class);
        verify(repository).insert(captor.capture());
        assertFalse(captor.getValue().getShared());
    }

    @Test
    void createEmailProvider_sharedTrue_adminSetsSharedTrue() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Test")
                .host("smtp.test.com")
                .username("user@test.com")
                .password("secret")
                .port(587)
                .shared(true)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.insert(any(EmailProviderV2Row.class))).thenReturn(savedRow);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());

        emailProviderV2Service.createEmailProvider(request);

        ArgumentCaptor<EmailProviderV2Row> captor = ArgumentCaptor.forClass(EmailProviderV2Row.class);
        verify(repository).insert(captor.capture());
        assertTrue(captor.getValue().getShared());
    }

    @Test
    void createEmailProvider_sharedTrue_nonAdminSetsSharedFalse() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Test")
                .host("smtp.test.com")
                .username("user@test.com")
                .password("secret")
                .port(587)
                .shared(true)
                .build();

        when(authService.getAuthenticatedUser()).thenReturn(regularUser);
        when(repository.insert(any(EmailProviderV2Row.class))).thenReturn(row(10L, 2L, false));
        when(preferenceRepository.findByUserId(2L)).thenReturn(Optional.empty());

        emailProviderV2Service.createEmailProvider(request);

        ArgumentCaptor<EmailProviderV2Row> captor = ArgumentCaptor.forClass(EmailProviderV2Row.class);
        verify(repository).insert(captor.capture());
        assertFalse(captor.getValue().getShared());
    }

    @Test
    void updateEmailProvider_nullShared_adminSetsSharedFalse() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Updated")
                .host("smtp.test.com")
                .port(587)
                .shared(null)
                .build();

        EmailProviderV2Row existing = row(10L, 1L, true);

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findByIdAndUserId(10L, 1L)).thenReturn(existing);
        when(repository.update(any(EmailProviderV2Row.class))).thenReturn(existing);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());

        emailProviderV2Service.updateEmailProvider(10L, request);

        ArgumentCaptor<EmailProviderV2Row> captor = ArgumentCaptor.forClass(EmailProviderV2Row.class);
        verify(repository).update(captor.capture());
        assertEquals(10L, captor.getValue().getId());
        assertFalse(captor.getValue().getShared());
    }

    @Test
    void updateEmailProvider_sharedTrue_adminSetsSharedTrue() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Updated")
                .host("smtp.test.com")
                .port(587)
                .shared(true)
                .build();

        EmailProviderV2Row existing = row(10L, 1L, false);

        when(authService.getAuthenticatedUser()).thenReturn(adminUser);
        when(repository.findByIdAndUserId(10L, 1L)).thenReturn(existing);
        when(repository.update(any(EmailProviderV2Row.class))).thenReturn(existing);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());

        emailProviderV2Service.updateEmailProvider(10L, request);

        ArgumentCaptor<EmailProviderV2Row> captor = ArgumentCaptor.forClass(EmailProviderV2Row.class);
        verify(repository).update(captor.capture());
        assertTrue(captor.getValue().getShared());
    }

    @Test
    void updateEmailProvider_sharedTrue_nonAdminDoesNotChangeShared() {
        CreateEmailProviderRequest request = CreateEmailProviderRequest.builder()
                .name("Updated")
                .host("smtp.test.com")
                .port(587)
                .shared(true)
                .build();

        EmailProviderV2Row existing = row(10L, 2L, false);

        when(authService.getAuthenticatedUser()).thenReturn(regularUser);
        when(repository.findByIdAndUserId(10L, 2L)).thenReturn(existing);
        when(repository.update(any(EmailProviderV2Row.class))).thenReturn(existing);
        when(preferenceRepository.findByUserId(2L)).thenReturn(Optional.empty());

        emailProviderV2Service.updateEmailProvider(10L, request);

        ArgumentCaptor<EmailProviderV2Row> captor = ArgumentCaptor.forClass(EmailProviderV2Row.class);
        verify(repository).update(captor.capture());
        // non-admin: request.shared(true) is ignored, keeps existing shared=false
        assertFalse(captor.getValue().getShared());
    }
}
