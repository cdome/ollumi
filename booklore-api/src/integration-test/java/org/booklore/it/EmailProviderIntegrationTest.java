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

public class EmailProviderIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanCrudEmailProvider() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> create = Map.of(
                "name", "SMTP IT",
                "host", "smtp.example.com",
                "port", 587,
                "username", "user",
                "password", "pass",
                "fromAddress", "from@example.com",
                "auth", true,
                "startTls", true,
                "shared", false
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/email/providers",
                auth.bearerEntity(create, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).containsKey("id");
        Integer id = (Integer) createResponse.getBody().get("id");

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/email/providers",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).anySatisfy(p ->
                assertThat(p.get("id")).isEqualTo(id)
        );

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/email/providers/" + id,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("host")).isEqualTo("smtp.example.com");

        Map<String, Object> update = Map.of(
                "name", "SMTP Updated",
                "host", "smtp2.example.com",
                "port", 465,
                "username", "user",
                "password", "newpass",
                "fromAddress", "from@example.com",
                "auth", true,
                "startTls", false,
                "shared", false
        );

        ResponseEntity<Map> updateResponse = rest.exchange(
                baseUrl() + "/api/v1/email/providers/" + id,
                HttpMethod.PUT,
                auth.bearerEntity(update, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("host")).isEqualTo("smtp2.example.com");

        ResponseEntity<Void> setDefaultResponse = rest.exchange(
                baseUrl() + "/api/v1/email/providers/" + id + "/set-default",
                HttpMethod.PATCH,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(setDefaultResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/email/providers/" + id,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
