package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.EmailProviderV2;
import org.booklore.model.dto.request.CreateEmailProviderRequest;
import org.booklore.repository.jooq.JooqEmailProviderV2Repository;
import org.booklore.repository.jooq.JooqUserEmailProviderPreferenceRepository;
import org.booklore.repository.jooq.dto.EmailProviderV2Row;
import org.booklore.repository.jooq.dto.UserEmailProviderPreference;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@AllArgsConstructor
public class EmailProviderV2Service {

    private final JooqEmailProviderV2Repository repository;
    private final JooqUserEmailProviderPreferenceRepository preferenceRepository;
    private final AuthenticationService authService;
    private final AuditService auditService;

    public List<EmailProviderV2> getEmailProviders() {
        BookLoreUser user = authService.getAuthenticatedUser();
        List<EmailProviderV2Row> userProviders = new ArrayList<>(repository.findAllByUserId(user.getId()));
        if (!user.getPermissions().isAdmin()) {
            userProviders.addAll(repository.findAllBySharedTrueAndAdmin());
        }

        Long defaultProviderId = getDefaultProviderIdForUser(user.getId());
        return userProviders.stream()
                .map(row -> toDto(row, defaultProviderId))
                .toList();
    }

    public EmailProviderV2 getEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Row row = repository.findAccessibleProvider(id, user.getId());
        if (row == null) {
            throw ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id);
        }
        return toDto(row, getDefaultProviderIdForUser(user.getId()));
    }

    @Transactional
    public EmailProviderV2 createEmailProvider(CreateEmailProviderRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        boolean shared = user.getPermissions().isAdmin() && Boolean.TRUE.equals(request.getShared());
        EmailProviderV2Row toInsert = new EmailProviderV2Row(
                0L,
                user.getId(),
                request.getName(),
                request.getHost(),
                request.getPort(),
                request.getUsername(),
                request.getPassword(),
                request.getFromAddress(),
                Boolean.TRUE.equals(request.getAuth()),
                Boolean.TRUE.equals(request.getStartTls()),
                false,
                shared);
        EmailProviderV2Row saved = repository.insert(toInsert);

        if (preferenceRepository.findByUserId(user.getId()).isEmpty()) {
            setDefaultProviderForUser(user.getId(), saved.getId());
        }

        auditService.log(AuditAction.EMAIL_PROVIDER_CREATED, "EmailProvider", saved.getId(), "Created email provider: " + saved.getHost() + ":" + saved.getPort());
        return toDto(saved, getDefaultProviderIdForUser(user.getId()));
    }

    @Transactional
    public EmailProviderV2 updateEmailProvider(Long id, CreateEmailProviderRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Row existing = repository.findByIdAndUserId(id, user.getId());
        if (existing == null) {
            throw ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id);
        }

        // Partial update: keep existing values where the request field is null (matches the old
        // MapStruct NullValuePropertyMappingStrategy.IGNORE behaviour). shared only changes for admins.
        boolean shared = user.getPermissions().isAdmin() ? Boolean.TRUE.equals(request.getShared()) : existing.getShared();
        EmailProviderV2Row merged = new EmailProviderV2Row(
                existing.getId(),
                existing.getUserId(),
                request.getName() != null ? request.getName() : existing.getName(),
                request.getHost() != null ? request.getHost() : existing.getHost(),
                request.getPort() != null ? request.getPort() : existing.getPort(),
                request.getUsername() != null ? request.getUsername() : existing.getUsername(),
                request.getPassword() != null ? request.getPassword() : existing.getPassword(),
                request.getFromAddress() != null ? request.getFromAddress() : existing.getFromAddress(),
                request.getAuth() != null ? request.getAuth() : existing.getAuth(),
                request.getStartTls() != null ? request.getStartTls() : existing.getStartTls(),
                existing.getDefaultProvider(),
                shared);
        EmailProviderV2Row updated = repository.update(merged);
        auditService.log(AuditAction.EMAIL_PROVIDER_UPDATED, "EmailProvider", id, "Updated email provider: " + updated.getHost() + ":" + updated.getPort());

        return toDto(updated, getDefaultProviderIdForUser(user.getId()));
    }

    @Transactional
    public void setDefaultEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        if (repository.findAccessibleProvider(id, user.getId()) == null) {
            throw ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id);
        }
        setDefaultProviderForUser(user.getId(), id);
    }

    @Transactional
    public void deleteEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        if (repository.findByIdAndUserId(id, user.getId()) == null) {
            throw ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id);
        }

        List<UserEmailProviderPreference> preferencesUsingProvider =
                preferenceRepository.findAllByDefaultProviderId(id);

        for (UserEmailProviderPreference preference : preferencesUsingProvider) {
            List<EmailProviderV2Row> availableProviders = getAccessibleProvidersForUser(preference.getUserId());
            availableProviders.removeIf(p -> p.getId() == id);

            if (!availableProviders.isEmpty()) {
                EmailProviderV2Row newDefault = availableProviders.get(ThreadLocalRandom.current().nextInt(availableProviders.size()));
                preferenceRepository.updateDefaultProviderById(preference.getId(), newDefault.getId());
            } else {
                preferenceRepository.deleteById(preference.getId());
            }
        }

        repository.deleteById(id);
        auditService.log(AuditAction.EMAIL_PROVIDER_DELETED, "EmailProvider", id, "Deleted email provider");
    }

    private Long getDefaultProviderIdForUser(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .map(UserEmailProviderPreference::getDefaultProviderId)
                .orElse(null);
    }

    private void setDefaultProviderForUser(Long userId, Long providerId) {
        preferenceRepository.upsertDefaultProvider(userId, providerId);
    }

    private List<EmailProviderV2Row> getAccessibleProvidersForUser(Long userId) {
        List<EmailProviderV2Row> providers = new ArrayList<>(repository.findAllByUserId(userId));
        providers.addAll(repository.findAllBySharedTrueAndAdmin());
        return providers;
    }

    private EmailProviderV2 toDto(EmailProviderV2Row row, Long defaultProviderId) {
        return EmailProviderV2.builder()
                .id(row.getId())
                .userId(row.getUserId())
                .name(row.getName())
                .host(row.getHost())
                .port(row.getPort())
                .username(row.getUsername())
                .fromAddress(row.getFromAddress())
                .auth(row.getAuth())
                .startTls(row.getStartTls())
                .defaultProvider(defaultProviderId != null && defaultProviderId == row.getId())
                .shared(row.getShared())
                .build();
    }
}
