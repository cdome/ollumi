package org.booklore.service.opds;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.OpdsUserV2;
import org.booklore.model.dto.request.OpdsUserV2CreateRequest;
import org.booklore.model.dto.request.OpdsUserV2UpdateRequest;
import org.booklore.model.enums.OpdsSortOrder;
import org.booklore.repository.UserRepository;
import org.booklore.repository.jooq.JooqOpdsUserV2Repository;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OpdsUserV2Service {

    private final JooqOpdsUserV2Repository opdsUserV2Repository;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;


    public List<OpdsUserV2> getOpdsUsers() {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
        return opdsUserV2Repository.findByUserId(bookLoreUser.getId());
    }

    public OpdsUserV2 createOpdsUser(OpdsUserV2CreateRequest request) {
        try {
            BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
            userRepository.findById(bookLoreUser.getId())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + bookLoreUser.getId()));

            OpdsUserV2 result = opdsUserV2Repository.insert(
                    bookLoreUser.getId(),
                    request.getUsername(),
                    passwordEncoder.encode(request.getPassword()),
                    request.getSortOrder() != null ? request.getSortOrder() : OpdsSortOrder.RECENT);
            auditService.log(AuditAction.OPDS_USER_CREATED, "OpdsUser", result.getId(), "Created OPDS user: " + request.getUsername());
            return result;
        } catch (DataIntegrityViolationException e) {
            if (e.getMostSpecificCause().getMessage().contains("uq_username")) {
                throw new DataIntegrityViolationException("Username '" + request.getUsername() + "' is already taken");
            }
            throw e;
        }
    }

    public void deleteOpdsUser(Long userId) {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
        OpdsUserV2 user = opdsUserV2Repository.findById(userId).orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        if (!user.getUserId().equals(bookLoreUser.getId())) {
            throw new AccessDeniedException("You are not allowed to delete this user");
        }
        String username = user.getUsername();
        opdsUserV2Repository.deleteById(userId);
        auditService.log(AuditAction.OPDS_USER_DELETED, "OpdsUser", userId, "Deleted OPDS user: " + username);
    }

    public OpdsUserV2 updateOpdsUser(Long userId, OpdsUserV2UpdateRequest request) {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
        OpdsUserV2 user = opdsUserV2Repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        if (!user.getUserId().equals(bookLoreUser.getId())) {
            throw new AccessDeniedException("You are not allowed to update this user");
        }

        OpdsUserV2 result = opdsUserV2Repository.updateSortOrder(userId, request.sortOrder());
        auditService.log(AuditAction.OPDS_USER_UPDATED, "OpdsUser", userId, "Updated OPDS user: " + user.getUsername());
        return result;
    }

    public OpdsUserV2 findByUsername(String username) {
        return opdsUserV2Repository.findByUsername(username).orElse(null);
    }
}
