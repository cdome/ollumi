package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookLoreUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ContentRestrictionIntegrationTest extends RestApiIntegrationTest {

    @Test
    void adminCanAddContentRestriction() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookLoreUserEntity target = auth.createUser("cr-target", "password");

        Map<String, Object> body = Map.of(
                "restrictionType", "CATEGORY",
                "mode", "EXCLUDE",
                "value", "Mature"
        );

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/users/" + target.getId() + "/content-restrictions",
                auth.bearerEntity(body, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("restrictionType")).isEqualTo("CATEGORY");
    }

    @Test
    void adminCanListUserRestrictions() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookLoreUserEntity target = auth.createUser("cr-list", "password");
        addRestrictionForUser(target.getId(), tokens);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/users/" + target.getId() + "/content-restrictions",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void userCanListOwnRestrictions() {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookLoreUserEntity target = auth.createUser("cr-self", "password");
        addRestrictionForUser(target.getId(), adminTokens);

        AuthTestHelper.Tokens userTokens = auth.login(baseUrl(), target.getUsername(), "password");

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/users/" + target.getId() + "/content-restrictions",
                HttpMethod.GET,
                auth.bearerEntity(userTokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void userCannotListOtherRestrictions() {
        AuthTestHelper.Tokens adminTokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookLoreUserEntity target = auth.createUser("cr-other", "password");
        BookLoreUserEntity other = auth.createUser("cr-other2", "password");
        addRestrictionForUser(target.getId(), adminTokens);

        AuthTestHelper.Tokens otherTokens = auth.login(baseUrl(), other.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/" + target.getId() + "/content-restrictions",
                HttpMethod.GET,
                auth.bearerEntity(otherTokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanUpdateRestrictions() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookLoreUserEntity target = auth.createUser("cr-update", "password");

        Map<String, Object> restriction = Map.of(
                "restrictionType", "TAG",
                "mode", "ALLOW_ONLY",
                "value", "Safe"
        );

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/users/" + target.getId() + "/content-restrictions",
                HttpMethod.PUT,
                auth.bearerEntity(List.of(restriction), tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void adminCanDeleteRestriction() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        BookLoreUserEntity target = auth.createUser("cr-delete", "password");
        ResponseEntity<Map> created = addRestrictionForUser(target.getId(), tokens);
        Integer id = (Integer) created.getBody().get("id");

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/users/" + target.getId() + "/content-restrictions/" + id,
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private ResponseEntity<Map> addRestrictionForUser(Long userId, AuthTestHelper.Tokens tokens) {
        Map<String, Object> body = Map.of(
                "restrictionType", "CATEGORY",
                "mode", "EXCLUDE",
                "value", "Mature"
        );
        return rest.postForEntity(
                baseUrl() + "/api/v1/users/" + userId + "/content-restrictions",
                auth.bearerEntity(body, tokens.accessToken()),
                Map.class
        );
    }
}
