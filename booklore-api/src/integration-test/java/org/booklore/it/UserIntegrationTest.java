package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookLoreUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class UserIntegrationTest extends RestApiIntegrationTest {

    @Test
    void meReturnsCurrentUser() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/me",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("username")).isEqualTo(ADMIN_USERNAME);
    }

    @Test
    void adminCanListAllUsers() {
        auth.createUser("user-list", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/users",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void regularUserCannotListAllUsers() {
        BookLoreUserEntity regular = auth.createUser("user-no-list", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), regular.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanViewAnyUserProfile() {
        BookLoreUserEntity user = auth.createUser("user-view", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/" + user.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("username")).isEqualTo(user.getUsername());
    }

    @Test
    void regularUserCanViewOwnProfile() {
        BookLoreUserEntity user = auth.createUser("user-own", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/" + user.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(user.getId().intValue());
    }

    @Test
    void regularUserCannotViewOtherProfile() {
        BookLoreUserEntity user = auth.createUser("user-other", "password");
        BookLoreUserEntity other = auth.createUser("user-other-target", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/" + other.getId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanUpdateUserPermissions() {
        BookLoreUserEntity user = auth.createUser("user-update", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> permissions = new HashMap<>();
        permissions.put("isAdmin", false);
        permissions.put("canUpload", true);
        permissions.put("canDownload", true);
        permissions.put("canEditMetadata", true);
        permissions.put("canManageLibrary", true);
        permissions.put("canEmailBook", false);
        permissions.put("canDeleteBook", false);
        permissions.put("canAccessOpds", false);
        permissions.put("canSyncKoReader", false);
        permissions.put("canSyncKobo", false);
        permissions.put("canManageMetadataConfig", false);
        permissions.put("canAccessBookdrop", false);
        permissions.put("canAccessLibraryStats", false);
        permissions.put("canAccessUserStats", false);
        permissions.put("canAccessTaskManager", false);
        permissions.put("canManageGlobalPreferences", false);
        permissions.put("canManageIcons", false);
        permissions.put("canManageFonts", false);
        permissions.put("canBulkAutoFetchMetadata", false);
        permissions.put("canBulkCustomFetchMetadata", false);
        permissions.put("canBulkEditMetadata", false);
        permissions.put("canBulkRegenerateCover", false);
        permissions.put("canMoveOrganizeFiles", false);
        permissions.put("canBulkLockUnlockMetadata", false);
        permissions.put("canBulkResetBookloreReadProgress", false);
        permissions.put("canBulkResetKoReaderReadProgress", false);
        permissions.put("canBulkResetBookReadStatus", false);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Updated Name");
        body.put("email", user.getEmail());
        body.put("permissions", permissions);
        body.put("assignedLibraries", List.of());

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/" + user.getId(),
                HttpMethod.PUT,
                auth.bearerEntity(body, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Updated Name");
    }

    @Test
    void adminCanChangeUserPassword() {
        BookLoreUserEntity user = auth.createUser("user-pwd-change", "old-password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> body = Map.of("userId", user.getId(), "newPassword", "new-password");
        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/change-user-password",
                HttpMethod.PUT,
                auth.bearerEntity(body, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthTestHelper.Tokens userTokens = auth.login(baseUrl(), user.getUsername(), "new-password");
        assertThat(userTokens.accessToken()).isNotBlank();
    }

    @Test
    void userCanChangeOwnPassword() {
        BookLoreUserEntity user = auth.createUser("user-own-pwd", "current-password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "current-password");

        Map<String, Object> body = Map.of("currentPassword", "current-password", "newPassword", "changed-password");
        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/users/change-password",
                HttpMethod.PUT,
                auth.bearerEntity(body, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthTestHelper.Tokens newTokens = auth.login(baseUrl(), user.getUsername(), "changed-password");
        assertThat(newTokens.accessToken()).isNotBlank();
    }

    @Test
    void adminCanDeleteUser() {
        BookLoreUserEntity user = auth.createUser("user-delete", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/v1/users/" + user.getId(),
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
