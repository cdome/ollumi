package org.booklore.it;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Setup flow is intentionally disabled for the shared-integration-test container:
 * the base test suite seeds an admin before every test, and setup is only possible
 * when NO local users exist. Testing setup reliably requires either a dedicated
 * database context or a full user-table cleanup, which is too invasive for Phase 1.
 */
@Disabled("Requires isolated DB state; implement in Phase 2 with dedicated context")
public class SetupIntegrationTest extends RestApiIntegrationTest {

    @Test
    void setupFlowPlaceholder() {
        ResponseEntity<Map> status = rest.getForEntity(
                baseUrl() + "/api/v1/setup/status",
                Map.class
        );
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
