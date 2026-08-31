package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AppSettingIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanGetAppSettings() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/settings",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("autoBookSearch");
        assertThat(response.getBody()).containsKey("pdfCacheSizeInMb");
    }

    @Test
    void adminCanUpdateAppSetting() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> setting = Map.of(
                "name", "AUTO_BOOK_SEARCH",
                "value", false
        );

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/settings",
                HttpMethod.PUT,
                auth.bearerEntity(List.of(setting), tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/settings",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().get("autoBookSearch")).isEqualTo(false);

        Map<String, Object> reset = Map.of(
                "name", "AUTO_BOOK_SEARCH",
                "value", true
        );
        rest.exchange(
                baseUrl() + "/api/v1/settings",
                HttpMethod.PUT,
                auth.bearerEntity(List.of(reset), tokens.accessToken()),
                Void.class
        );
    }

    @Test
    void unauthenticatedGetSettingsReturnsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/v1/settings", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
