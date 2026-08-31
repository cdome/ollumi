package org.booklore.it;

import org.booklore.it.util.AuthTestHelper;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.MetadataFetchJobEntity;
import org.booklore.model.entity.MetadataFetchProposalEntity;
import org.booklore.model.enums.FetchedMetadataProposalStatus;
import org.booklore.model.enums.MetadataFetchTaskStatus;
import org.booklore.repository.MetadataFetchJobRepository;
import org.booklore.repository.MetadataFetchProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class MetadataTaskIntegrationTest extends RestApiIntegrationTest {

    @Autowired
    private MetadataFetchJobRepository metadataFetchJobRepository;

    @Autowired
    private MetadataFetchProposalRepository metadataFetchProposalRepository;

    @BeforeEach
    void cleanMetadataTaskState() {
        metadataFetchProposalRepository.deleteAll();
        metadataFetchJobRepository.deleteAll();
    }

    @Test
    void adminCanGetMetadataTaskWithProposals() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        MetadataFetchJobEntity job = createJobWithProposal();

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/metadata/tasks/" + job.getTaskId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("task");
        Map<String, Object> task = (Map<String, Object>) response.getBody().get("task");
        assertThat(task.get("id")).isEqualTo(job.getTaskId());
        assertThat(task.get("proposals")).asList().hasSize(1);
    }

    @Test
    void getMissingMetadataTaskReturnsNotFound() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/metadata/tasks/missing-task-id",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void userWithoutMetadataEditPermissionCannotAccessTasks() {
        BookLoreUserEntity user = auth.createUser("metadata-no-edit", "password");
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), user.getUsername(), "password");
        MetadataFetchJobEntity job = createJobWithProposal();

        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + "/api/metadata/tasks/" + job.getTaskId(),
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanListActiveMetadataTasks() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        createJobWithProposal();

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                baseUrl() + "/api/metadata/tasks/active",
                HttpMethod.GET,
                auth.bearerEntity(tokens.accessToken()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("taskId")).isNotNull();
    }

    @Test
    void adminCanDeleteMetadataTask() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        MetadataFetchJobEntity job = createJobWithProposal();

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/metadata/tasks/" + job.getTaskId(),
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(metadataFetchJobRepository.findById(job.getTaskId())).isEmpty();
    }

    @Test
    void deleteMissingMetadataTaskReturnsNotFound() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/metadata/tasks/missing-task-id",
                HttpMethod.DELETE,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminCanUpdateProposalStatus() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        MetadataFetchJobEntity job = createJobWithProposal();
        Long proposalId = job.getProposals().get(0).getProposalId();

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/metadata/tasks/" + job.getTaskId() + "/proposals/" + proposalId + "/status?status=ACCEPTED",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        MetadataFetchProposalEntity updated = metadataFetchProposalRepository.findById(proposalId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(FetchedMetadataProposalStatus.ACCEPTED);
        assertThat(updated.getReviewerUserId()).isEqualTo(auth.userRepository().findByUsername(ADMIN_USERNAME).orElseThrow().getId());
        assertThat(updated.getReviewedAt()).isNotNull();
    }

    @Test
    void updateStatusForMissingProposalReturnsNotFound() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        MetadataFetchJobEntity job = createJobWithProposal();

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/metadata/tasks/" + job.getTaskId() + "/proposals/99999/status?status=ACCEPTED",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidProposalStatusReturnsNotFound() {
        AuthTestHelper.Tokens tokens = auth.login(baseUrl(), ADMIN_USERNAME, ADMIN_PASSWORD);
        MetadataFetchJobEntity job = createJobWithProposal();
        Long proposalId = job.getProposals().get(0).getProposalId();

        ResponseEntity<Void> response = rest.exchange(
                baseUrl() + "/api/metadata/tasks/" + job.getTaskId() + "/proposals/" + proposalId + "/status?status=INVALID",
                HttpMethod.POST,
                auth.bearerEntity(tokens.accessToken()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private MetadataFetchJobEntity createJobWithProposal() {
        String taskId = UUID.randomUUID().toString();
        MetadataFetchJobEntity job = MetadataFetchJobEntity.builder()
                .taskId(taskId)
                .userId(auth.userRepository().findByUsername(ADMIN_USERNAME).orElseThrow().getId())
                .status(MetadataFetchTaskStatus.COMPLETED)
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .totalBooksCount(1)
                .completedBooks(1)
                .build();

        MetadataFetchProposalEntity proposal = MetadataFetchProposalEntity.builder()
                .job(job)
                .bookId(1L)
                .status(FetchedMetadataProposalStatus.FETCHED)
                .fetchedAt(Instant.now())
                .metadataJson("{}")
                .build();

        job.setProposals(List.of(proposal));
        return metadataFetchJobRepository.save(job);
    }
}
