package org.booklore.config.security.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.oidc.OidcDiscoveryService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.response.LogoutResponse;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.jooq.dto.RefreshToken;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.ProvisioningMethod;

import org.booklore.repository.jooq.JooqRefreshTokenRepository;
import org.booklore.repository.jooq.JooqOidcSessionRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

@Slf4j
@Service
@AllArgsConstructor
public class LogoutService {

    private final JooqRefreshTokenRepository refreshTokenRepository;
    private final JooqOidcSessionRepository oidcSessionRepository;
    private final UserRepository userRepository;
    private final AppSettingService appSettingService;
    private final OidcDiscoveryService discoveryService;
    private final AuditService auditService;
    private final AuthenticationService authenticationService;

    public LogoutResponse logout(Authentication auth, String refreshToken, String origin) {
        BookLoreUserEntity user = resolveUser(auth, refreshToken);

        revokeRefreshToken(user);

        String logoutUrl = null;
        if (user.getProvisioningMethod() == ProvisioningMethod.OIDC && appSettingService.getAppSettings().isOidcEnabled()) {
            logoutUrl = buildOidcLogoutUrl(user, origin);
        }

        auditService.log(AuditAction.LOGOUT, "User", user.getId(), "User logged out: " + user.getUsername());
        return new LogoutResponse(logoutUrl);
    }

    private BookLoreUserEntity resolveUser(Authentication auth, String refreshToken) {
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            var bookLoreUser = authenticationService.getAuthenticatedUser();
            return userRepository.findByUsername(bookLoreUser.getUsername())
                    .orElseThrow(() -> ApiError.GENERIC_UNAUTHORIZED.createException("User not found"));
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            RefreshToken tokenRecord = refreshTokenRepository.findByToken(refreshToken);
            if (tokenRecord == null || tokenRecord.getUserId() == null) {
                throw ApiError.GENERIC_UNAUTHORIZED.createException("Invalid refresh token");
            }
            return userRepository.findById(tokenRecord.getUserId())
                    .orElseThrow(() -> ApiError.GENERIC_UNAUTHORIZED.createException("User not found"));
        }

        throw ApiError.GENERIC_UNAUTHORIZED.createException("No authentication context or refresh token provided");
    }

    private void revokeRefreshToken(BookLoreUserEntity user) {
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
    }

    private String buildOidcLogoutUrl(BookLoreUserEntity user, String origin) {
        try {
            var providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
            var oidcSession = oidcSessionRepository.findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(user.getId());

            if (oidcSession != null) {
                oidcSessionRepository.revokeById(oidcSession.getId());

                var discovery = discoveryService.discover(providerDetails.getIssuerUri());
                if (discovery.endSessionEndpoint() != null) {
                    String postLogoutRedirectUri = (origin != null && !origin.isBlank() ? origin : "") + "/login";

                    var builder = UriComponentsBuilder.fromUriString(discovery.endSessionEndpoint())
                            .queryParam("client_id", providerDetails.getClientId())
                            .queryParam("id_token_hint", oidcSession.getIdTokenHint());

                    if (!postLogoutRedirectUri.equals("/login")) {
                        builder.queryParam("post_logout_redirect_uri", postLogoutRedirectUri);
                    }

                    return builder.build().toUriString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to build OIDC logout URL: {}", e.getMessage());
        }
        return null;
    }
}
