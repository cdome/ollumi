package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class IconIntegrationTest extends RestApiIntegrationTest {

    private static final String SVG_DATA = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"10\" height=\"10\"/></svg>";

    @Test
    void adminCanSaveRetrieveAndDeleteIcon() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        String iconName = "it-icon-" + UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "svgName", iconName,
                "svgData", SVG_DATA
        );

        ResponseEntity<Void> saveResponse = rest.postForEntity(
                baseUrl() + "/api/v1/icons",
                auth.bearerEntity(request, tokens.accessToken()),
                Void.class
        );

        assertThat(saveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> contentResponse = rest.exchange(
                baseUrl() + "/api/v1/icons/" + iconName + "/content",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                String.class
        );

        assertThat(contentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentResponse.getBody()).contains("<svg");

        ResponseEntity<Map> listResponse = rest.exchange(
                baseUrl() + "/api/v1/icons?page=0&size=50",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        List<String> content = (List<String>) listResponse.getBody().get("content");
        assertThat(content).contains(iconName);

        ResponseEntity<Map> allResponse = rest.exchange(
                baseUrl() + "/api/v1/icons/all/content",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(allResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allResponse.getBody()).containsKey(iconName);

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/icons/" + iconName,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminCanSaveBatchIcons() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        String firstIcon = "it-batch-icon-1-" + UUID.randomUUID();
        String secondIcon = "it-batch-icon-2-" + UUID.randomUUID();

        Map<String, Object> body = Map.of(
                "icons", List.of(
                        Map.of("svgName", firstIcon, "svgData", SVG_DATA),
                        Map.of("svgName", secondIcon, "svgData", SVG_DATA)
                )
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/icons/batch",
                auth.bearerEntity(body, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("totalRequested")).isEqualTo(2);
        assertThat(response.getBody().get("successCount")).isEqualTo(2);
        assertThat(response.getBody().get("failureCount")).isEqualTo(0);

        rest.exchange(
                baseUrl() + "/api/v1/icons/" + firstIcon,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );
        rest.exchange(
                baseUrl() + "/api/v1/icons/" + secondIcon,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );
    }
}
