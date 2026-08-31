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

public class EmailRecipientIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanCrudEmailRecipient() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> create = Map.of(
                "email", "reader@example.com",
                "name", "Reader",
                "defaultRecipient", false
        );

        ResponseEntity<Map> createResponse = rest.postForEntity(
                baseUrl() + "/api/v1/email/recipients",
                auth.bearerEntity(create, tokens.accessToken()),
                Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).containsKey("id");
        Integer id = (Integer) createResponse.getBody().get("id");

        ResponseEntity<List<Map<String, Object>>> listResponse = rest.exchange(
                baseUrl() + "/api/v1/email/recipients",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).anySatisfy(r ->
                assertThat(r.get("id")).isEqualTo(id)
        );

        ResponseEntity<Map> getResponse = rest.exchange(
                baseUrl() + "/api/v1/email/recipients/" + id,
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("email")).isEqualTo("reader@example.com");

        Map<String, Object> update = Map.of(
                "email", "reader2@example.com",
                "name", "Reader Two",
                "defaultRecipient", true
        );

        ResponseEntity<Map> updateResponse = rest.exchange(
                baseUrl() + "/api/v1/email/recipients/" + id,
                HttpMethod.PUT,
                auth.bearerEntity(update, tokens.accessToken()),
                Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("name")).isEqualTo("Reader Two");

        ResponseEntity<Void> setDefaultResponse = rest.exchange(
                baseUrl() + "/api/v1/email/recipients/" + id + "/set-default",
                HttpMethod.PATCH,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(setDefaultResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> deleteResponse = rest.exchange(
                baseUrl() + "/api/v1/email/recipients/" + id,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
