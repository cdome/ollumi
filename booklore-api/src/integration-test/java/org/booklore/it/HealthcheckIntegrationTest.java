package org.booklore.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthcheckIntegrationTest extends RestApiIntegrationTest {

    @Test
    void healthcheckReturnsOk() {
        ResponseEntity<Map> response = rest.getForEntity(baseUrl() + "/api/v1/healthcheck", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("data");
        assertThat(response.getBody()).containsKey("timestamp");
    }
}
