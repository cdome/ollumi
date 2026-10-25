package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.EmailRecipientV2;
import org.booklore.model.dto.request.CreateEmailRecipientRequest;
import org.booklore.repository.jooq.JooqEmailRecipientV2Repository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@AllArgsConstructor
public class EmailRecipientV2Service {

    private final JooqEmailRecipientV2Repository repository;
    private final AuthenticationService authService;

    public List<EmailRecipientV2> getEmailRecipients() {
        BookLoreUser user = authService.getAuthenticatedUser();
        return repository.findAllByUserId(user.getId());
    }

    public EmailRecipientV2 getEmailRecipient(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2 emailRecipient = repository.findByIdAndUserId(id, user.getId());
        if (emailRecipient == null) {
            throw ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id);
        }
        return emailRecipient;
    }

    @Transactional
    public EmailRecipientV2 createEmailRecipient(CreateEmailRecipientRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        boolean isFirstRecipient = repository.count() == 0;
        boolean makeDefault = request.isDefaultRecipient() || isFirstRecipient;
        if (makeDefault) {
            repository.updateAllRecipientsToNonDefault(user.getId());
        }
        return repository.insert(user.getId(), request.getEmail(), request.getName(), makeDefault);
    }

    @Transactional
    public EmailRecipientV2 updateEmailRecipient(Long id, CreateEmailRecipientRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2 existingRecipient = repository.findByIdAndUserId(id, user.getId());
        if (existingRecipient == null) {
            throw ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id);
        }
        if (request.isDefaultRecipient()) {
            repository.updateAllRecipientsToNonDefault(user.getId());
        }
        return repository.update(id, request.getEmail(), request.getName(), request.isDefaultRecipient());
    }

    @Transactional
    public void setDefaultRecipient(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2 emailRecipient = repository.findByIdAndUserId(id, user.getId());
        if (emailRecipient == null) {
            throw ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id);
        }
        repository.updateAllRecipientsToNonDefault(user.getId());
        repository.markDefaultById(id);
    }

    @Transactional
    public void deleteEmailRecipient(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailRecipientV2 emailRecipientToDelete = repository.findByIdAndUserId(id, user.getId());
        if (emailRecipientToDelete == null) {
            throw ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(id);
        }
        if (emailRecipientToDelete.isDefaultRecipient()) {
            List<EmailRecipientV2> allRecipients = new ArrayList<>(repository.findAll());
            if (allRecipients.size() > 1) {
                allRecipients.removeIf(r -> r.getId().equals(id));
                int randomIndex = ThreadLocalRandom.current().nextInt(allRecipients.size());
                repository.markDefaultById(allRecipients.get(randomIndex).getId());
            }
        }
        repository.deleteById(id);
    }
}
