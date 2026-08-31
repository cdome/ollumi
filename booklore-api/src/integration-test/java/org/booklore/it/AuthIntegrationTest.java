package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanLoginAndReceiveTokens() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    void loginWithWrongPasswordReturnsBadRequest() {
        ResponseEntity<Map> response = auth.tryLogin(baseUrl(), ADMIN_USERNAME, "wrong-password");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).isEqualTo("Invalid credentials");
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointReturnsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/v1/users/me", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void malformedTokenRequestReturnsUnauthorized() {
        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/me",
                org.springframework.http.HttpMethod.GET,
                auth.bearerEntity("invalid-token"),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticatedUserCanAccessMe() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/me",
                org.springframework.http.HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("username")).isEqualTo(ADMIN_USERNAME);
    }
}
