package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.FixtureFactory;
import org.booklore.model.dto.response.TasksHistoryResponse;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.TaskType;
import org.booklore.task.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TaskIntegrationTest extends RestApiIntegrationTest {

    @BeforeEach
    void cleanTaskState() {
        jdbc.update("DELETE FROM task_cron_configuration");
        jdbc.update("DELETE FROM tasks");
        jdbc.update("""
                UPDATE user_permissions
                SET permission_access_task_manager = 1
                WHERE user_id = (SELECT id FROM users WHERE username = ?)
                """, ADMIN_USERNAME);

        truncateBookTables();
    }

    private void truncateBookTables() {
        jdbc.update("SET FOREIGN_KEY_CHECKS = 0");
        String[] tables = {
                "book_metadata_author_mapping",
                "book_metadata_category_mapping",
                "book_metadata_mood_mapping",
                "book_metadata_tag_mapping",
                "book_review",
                "book_file",
                "book_metadata",
                "user_book_file_progress",
                "user_book_progress",
                "book_shelf_mapping",
                "books",
                "library_path",
                "library"
        };
        for (String table : tables) {
            try {
                jdbc.update("TRUNCATE TABLE " + table);
            } catch (Exception ignored) {
                // table may not exist in current schema
            }
        }
        jdbc.update("SET FOREIGN_KEY_CHECKS = 1");
    }

    @AfterEach
    void waitForRunningRefreshTask() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<TasksHistoryResponse> response = rest.exchange(
                    baseUrl() + "/api/v1/tasks/last",
                    HttpMethod.GET,
                    auth.bearerEntity(tokens.accessToken()),
                    TasksHistoryResponse.class
            );
            if (response.getBody() == null || response.getBody().getTaskHistories() == null) {
                return;
            }
            boolean running = response.getBody().getTaskHistories().stream()
                    .filter(h -> h.getType() == TaskType.REFRESH_LIBRARY_METADATA)
                    .anyMatch(h -> h.getStatus() == TaskStatus.ACCEPTED || h.getStatus() == TaskStatus.IN_PROGRESS);
            if (!running) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void adminCanListAvailableTasks() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/tasks",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).extracting("taskType")
                .contains("CLEANUP_DELETED_BOOKS", "SYNC_LIBRARY_FILES", "CLEANUP_TEMP_METADATA");
    }

    @Test
    void userWithTaskManagerPermissionCanListTasks() {
        BookLoreUserEntity user = auth.createUser("task-user", "password",
                perms -> perms.setPermissionAccessTaskManager(true));
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/v1/tasks",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void regularUserCannotListTasks() {
        BookLoreUserEntity user = auth.createUser("task-no-perm", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/tasks",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanStartSafeTask() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> options = Map.of(
                "updateMetadataFromFiles", false,
                "metadataReplaceMode", "REPLACE_MISSING"
        );
        Map<String, Object> request = Map.of(
                "taskType", "REFRESH_LIBRARY_METADATA",
                "options", options
        );
        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/tasks/start",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsKey("taskId");
        assertThat(response.getBody().get("taskType")).isEqualTo("REFRESH_LIBRARY_METADATA");
    }

    @Test
    void startTaskRequiresTaskManagerPermission() {
        BookLoreUserEntity user = auth.createUser("task-start-no-perm", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");

        Map<String, Object> options = Map.of(
                "updateMetadataFromFiles", false,
                "metadataReplaceMode", "REPLACE_MISSING"
        );
        Map<String, Object> request = Map.of(
                "taskType", "REFRESH_LIBRARY_METADATA",
                "options", options
        );
        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + "/api/v1/tasks/start",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanCancelRunningAsyncTask() throws Exception {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        // Seed some books so the metadata refresh task stays running long enough to cancel
        Path libraryDir = Files.createTempDirectory("cancel-task-lib-");
        LibraryEntity library = data.createLibrary("Cancel Task Library " + UUID.randomUUID(), libraryDir);
        for (int i = 0; i < 3; i++) {
            Path pdf = Files.createTempFile("cancel-book-" + i + "-", ".pdf");
            FixtureFactory.writePdf(pdf);
            data.createBookWithFile(library, "Cancel Book " + i + " " + UUID.randomUUID(), BookFileType.PDF, pdf);
            Files.deleteIfExists(pdf);
        }

        Map<String, Object> options = Map.of(
                "updateMetadataFromFiles", true,
                "metadataReplaceMode", "REPLACE_MISSING"
        );
        Map<String, Object> request = Map.of(
                "taskType", "REFRESH_LIBRARY_METADATA",
                "options", options
        );
        ResponseEntity<Map> startResponse = rest.postForEntity(
                baseUrl() + "/api/v1/tasks/start",
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String taskId = (String) startResponse.getBody().get("taskId");

        ResponseEntity<Map> cancelResponse = rest.exchange(
                baseUrl() + "/api/v1/tasks/" + taskId + "/cancel",
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody().get("cancelled")).isEqualTo(true);
        assertThat(cancelResponse.getBody().get("taskId")).isEqualTo(taskId);
    }

    @Test
    void cancelNonRunningTaskReturnsNotFound() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/tasks/not-a-task/cancel",
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminCanGetLatestTasksForEachType() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/tasks/last",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("taskHistories");
    }

    @Test
    void adminCanPatchCronConfigForSupportedTask() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> request = Map.of(
                "cronExpression", "0 0 0 * * *",
                "enabled", true
        );

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/tasks/CLEANUP_DELETED_BOOKS/cron",
                HttpMethod.PATCH,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("taskType")).isEqualTo("CLEANUP_DELETED_BOOKS");
        assertThat(response.getBody().get("cronExpression")).isEqualTo("0 0 0 * * *");
        assertThat(response.getBody().get("enabled")).isEqualTo(true);
    }

    @Test
    void patchCronConfigForUnsupportedTaskReturnsBadRequest() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> request = Map.of(
                "cronExpression", "0 0 0 * * *",
                "enabled", true
        );

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/tasks/REFRESH_LIBRARY_METADATA/cron",
                HttpMethod.PATCH,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void invalidCronExpressionReturnsBadRequest() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        Map<String, Object> request = Map.of(
                "cronExpression", "invalid",
                "enabled", true
        );

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/v1/tasks/CLEANUP_DELETED_BOOKS/cron",
                HttpMethod.PATCH,
                auth.bearerEntity(request, tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
