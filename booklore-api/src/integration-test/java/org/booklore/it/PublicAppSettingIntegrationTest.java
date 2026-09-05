package org.booklore.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PublicAppSettingIntegrationTest extends RestApiIntegrationTest {

    @Test
    void publicSettingsAreAccessibleWithoutAuthentication() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/v1/public-settings", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("oidcEnabled");
        assertThat(response.getBody()).containsKey("remoteAuthEnabled");
        assertThat(response.getBody()).containsKey("oidcForceOnlyMode");
    }
}
