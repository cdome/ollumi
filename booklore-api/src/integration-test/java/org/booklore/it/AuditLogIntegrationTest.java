package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.repository.jooq.JooqAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AuditLogIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private JooqAuditLogRepository auditLogRepository;

    @Test
    void adminCanListAuditLogs() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/audit-logs?page=0&size=25",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
    }

    @Test
    void adminCanFilterAuditLogsByAction() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/audit-logs?action=LOGIN_SUCCESS&page=0&size=25",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).allMatch(e -> "LOGIN_SUCCESS".equals(e.get("action")));
    }

    @Test
    void adminCanFilterAuditLogsByUsername() {
        auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        auditLogRepository.insert(null, "audit-filter-user", AuditAction.USER_CREATED,
                null, null, "audit log for filter test", null, null);

        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/audit-logs?username=audit-filter-user&page=0&size=25",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allMatch(e -> "audit-filter-user".equals(e.get("username")));
    }

    @Test
    void adminCanGetDistinctUsernames() {
        auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        auditLogRepository.insert(null, "distinct-user", AuditAction.LOGOUT,
                null, null, "distinct user audit log", null, null);

        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<List<String>> response = rest.exchange(
                baseUrl() + "/api/v1/audit-logs/usernames",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("distinct-user");
    }

    @Test
    void regularUserCannotAccessAuditLogs() {
        BookLoreUserEntity user = auth.createUser("audit-regular-user", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/audit-logs?page=0&size=25",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
