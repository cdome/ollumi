package org.booklore.it;

import jakarta.servlet.http.HttpServletRequest;
import org.booklore.config.security.oidc.OidcAuthService;
import org.booklore.config.security.oidc.OidcCallbackRequest;
import org.booklore.config.security.oidc.OidcStateService;
import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.service.oidc.OidcDiagnosticService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class OidcIntegrationTest extends RestApiIntegrationTest {

    @MockitoBean
    private OidcAuthService oidcAuthService;

    @MockitoBean
    private OidcStateService oidcStateService;

    @MockitoBean
    private OidcDiagnosticService oidcDiagnosticService;

    @AfterEach
    void resetMocks() {
        reset(oidcAuthService, oidcStateService, oidcDiagnosticService);
    }

    @Test
    void oidcCallbackReturnsTokensFromMockedService() {
        doNothing().when(oidcStateService).validateAndConsume(anyString());
        when(oidcAuthService.exchangeCodeForTokens(
                anyString(), anyString(), anyString(), anyString(), any(HttpServletRequest.class)
        )).thenReturn(ResponseEntity.ok(Map.of("accessToken", "a", "refreshToken", "r")));

        OidcCallbackRequest request = new OidcCallbackRequest("code", "verifier", "http://localhost/oauth2-callback", "nonce", "state");

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/auth/oidc/callback",
                request,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("accessToken")).isEqualTo("a");
        assertThat(response.getBody().get("refreshToken")).isEqualTo("r");
    }

    @Test
    void oidcMobileCallbackReturnsTokensFromMockedService() {
        doNothing().when(oidcStateService).validateAndConsume(anyString());
        when(oidcAuthService.exchangeCodeForTokens(
                anyString(), anyString(), anyString(), anyString(), any(HttpServletRequest.class)
        )).thenReturn(ResponseEntity.ok(Map.of("accessToken", "a", "refreshToken", "r")));

        String url = baseUrl()
                + "/api/v1/auth/oidc/mobile/callback"
                + "?code=code"
                + "&code_verifier=verifier"
                + "&redirect_uri=http://localhost/oauth2-callback"
                + "&nonce=nonce"
                + "&state=state";

        ResponseEntity<Map> response = rest.postForEntity(url, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("accessToken")).isEqualTo("a");
        assertThat(response.getBody().get("refreshToken")).isEqualTo("r");
    }

    @Test
    void adminCanRunOidcConnectionDiagnostic() {
        OidcDiagnosticService.OidcTestResult expected = new OidcDiagnosticService.OidcTestResult(true, List.of());
        when(oidcDiagnosticService.testConnection(any(OidcProviderDetails.class))).thenReturn(expected);

        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        OidcProviderDetails providerDetails = new OidcProviderDetails();
        providerDetails.setProviderName("Mock Provider");
        providerDetails.setClientId("client-id");
        providerDetails.setIssuerUri("http://mock-issuer");

        HttpEntity<OidcProviderDetails> entity = auth.bearerEntity(providerDetails, tokens.accessToken());
        ResponseEntity<OidcDiagnosticService.OidcTestResult> response = rest.exchange(
                baseUrl() + "/api/v1/settings/oidc/test",
                HttpMethod.POST,
                entity,
                OidcDiagnosticService.OidcTestResult.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
    }
}
