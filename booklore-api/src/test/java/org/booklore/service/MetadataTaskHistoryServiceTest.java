package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.FetchedProposalMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.FetchedProposal;
import org.booklore.model.dto.MetadataBatchProgressNotification;
import org.booklore.model.dto.MetadataFetchTask;
import org.booklore.model.dto.response.MetadataTaskDetailsResponse;
import org.booklore.model.enums.FetchedMetadataProposalStatus;
import org.booklore.model.enums.MetadataFetchTaskStatus;
import org.booklore.repository.jooq.JooqMetadataFetchJobRepository;
import org.booklore.repository.jooq.dto.MetadataFetchJobRow;
import org.booklore.repository.jooq.dto.MetadataFetchProposalRow;
import org.booklore.service.metadata.MetadataTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MetadataTaskHistoryServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2025-01-01T12:00:00Z");

    @Mock
    private JooqMetadataFetchJobRepository metadataFetchTaskRepository;

    @Mock
    private FetchedProposalMapper fetchedProposalMapper;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private MetadataTaskService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private MetadataFetchProposalRow proposalRow(Long proposalId, String taskId, FetchedMetadataProposalStatus status) {
        return new MetadataFetchProposalRow(proposalId, taskId, 1L, FIXED_INSTANT, null, null, status, "{}");
    }

    @Test
    void getTaskWithProposals_shouldReturnEmptyWhenNoTaskFound() {
        when(metadataFetchTaskRepository.findById("task1")).thenReturn(Optional.empty());

        Optional<MetadataTaskDetailsResponse> result = service.getTaskWithProposals("task1");

        assertThat(result).isEmpty();
        verify(metadataFetchTaskRepository).findById("task1");
        verifyNoInteractions(fetchedProposalMapper);
    }

    @Test
    void getTaskWithProposals_shouldReturnTaskWithFilteredProposals() {
        MetadataFetchProposalRow p1 = proposalRow(1L, "task1", FetchedMetadataProposalStatus.FETCHED);
        MetadataFetchProposalRow p2 = proposalRow(2L, "task1", FetchedMetadataProposalStatus.ACCEPTED);

        MetadataFetchJobRow jobRow = new MetadataFetchJobRow(
                "task1", 99L, MetadataFetchTaskStatus.IN_PROGRESS, null,
                FIXED_INSTANT.minusSeconds(60), null, 3, 2, List.of(p1, p2));

        when(metadataFetchTaskRepository.findById("task1")).thenReturn(Optional.of(jobRow));

        FetchedProposal dto1 = mock(FetchedProposal.class);
        when(fetchedProposalMapper.toDto(p1)).thenReturn(dto1);

        Optional<MetadataTaskDetailsResponse> optResponse = service.getTaskWithProposals("task1");
        assertThat(optResponse).isPresent();

        MetadataTaskDetailsResponse response = optResponse.get();
        MetadataFetchTask taskDto = response.getTask();

        assertThat(taskDto.getId()).isEqualTo("task1");
        assertThat(taskDto.getStatus()).isEqualTo(MetadataFetchTaskStatus.IN_PROGRESS);
        assertThat(taskDto.getCompleted()).isEqualTo(2);
        assertThat(taskDto.getTotalBooks()).isEqualTo(3);
        assertThat(taskDto.getStartedAt()).isEqualTo(FIXED_INSTANT.minusSeconds(60));
        assertThat(taskDto.getCompletedAt()).isNull();
        assertThat(taskDto.getInitiatedBy()).isEqualTo(99L);
        assertThat(taskDto.getProposals()).containsExactly(dto1);

        verify(metadataFetchTaskRepository).findById("task1");
        verify(fetchedProposalMapper).toDto(p1);
    }

    @Test
    void deleteTaskAndProposals_shouldDeleteWhenTaskExists() {
        when(metadataFetchTaskRepository.deleteById("task1")).thenReturn(true);

        boolean result = service.deleteTaskAndProposals("task1");

        assertThat(result).isTrue();
        verify(metadataFetchTaskRepository).deleteById("task1");
    }

    @Test
    void deleteTaskAndProposals_shouldReturnFalseWhenTaskMissing() {
        when(metadataFetchTaskRepository.deleteById("missing")).thenReturn(false);

        boolean result = service.deleteTaskAndProposals("missing");

        assertThat(result).isFalse();
        verify(metadataFetchTaskRepository).deleteById("missing");
    }

    @Test
    void updateProposalStatus_shouldReturnFalseIfInvalidStatus() {
        BookLoreUser mockedUser = mock(BookLoreUser.class);
        when(mockedUser.getId()).thenReturn(1L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(mockedUser);
        boolean result = service.updateProposalStatus("task1", 1L, "INVALID_STATUS");
        assertThat(result).isFalse();
        verifyNoInteractions(metadataFetchTaskRepository);
    }


    @Test
    void updateProposalStatus_shouldReturnFalseIfProposalNotFoundOrJobMismatch() {
        BookLoreUser mockedUser = mock(BookLoreUser.class);
        when(mockedUser.getId()).thenReturn(123L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(mockedUser);

        when(metadataFetchTaskRepository.findProposalById(1L)).thenReturn(Optional.empty());

        boolean result = service.updateProposalStatus("task1", 1L, "ACCEPTED");
        assertThat(result).isFalse();

        MetadataFetchProposalRow proposal = proposalRow(2L, "otherTask", FetchedMetadataProposalStatus.FETCHED);
        when(metadataFetchTaskRepository.findProposalById(2L)).thenReturn(Optional.of(proposal));

        boolean result2 = service.updateProposalStatus("task1", 2L, "REJECTED");
        assertThat(result2).isFalse();
    }

    @Test
    void updateProposalStatus_shouldUpdateProposalWhenValid() {
        MetadataFetchProposalRow proposal = proposalRow(10L, "task1", FetchedMetadataProposalStatus.FETCHED);

        BookLoreUser mockedUser = mock(BookLoreUser.class);
        when(mockedUser.getId()).thenReturn(42L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(mockedUser);

        when(metadataFetchTaskRepository.findProposalById(10L)).thenReturn(Optional.of(proposal));

        boolean result = service.updateProposalStatus("task1", 10L, "ACCEPTED");

        assertThat(result).isTrue();
        verify(metadataFetchTaskRepository).updateProposalReview(
                eq(10L), eq(FetchedMetadataProposalStatus.ACCEPTED), any(Instant.class), eq(42L));
    }

    @Test
    void getActiveTasks_shouldReturnOnlyTasksWithRemainingProposals() {
        MetadataFetchProposalRow p1 = proposalRow(1L, "task1", FetchedMetadataProposalStatus.ACCEPTED);
        MetadataFetchProposalRow p2 = proposalRow(2L, "task1", FetchedMetadataProposalStatus.REJECTED);
        MetadataFetchJobRow job1 = new MetadataFetchJobRow(
                "task1", 1L, MetadataFetchTaskStatus.COMPLETED, null,
                FIXED_INSTANT, null, 2, 2, List.of(p1, p2));

        MetadataFetchProposalRow p3 = proposalRow(3L, "task2", FetchedMetadataProposalStatus.FETCHED);
        MetadataFetchJobRow job2 = new MetadataFetchJobRow(
                "task2", 1L, MetadataFetchTaskStatus.COMPLETED, null,
                FIXED_INSTANT, null, 1, 1, List.of(p3));

        when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(job1, job2));

        List<MetadataBatchProgressNotification> notifications = service.getActiveTasks();

        assertThat(notifications).hasSize(2);

        MetadataBatchProgressNotification n1 = notifications.stream()
                .filter(n -> n.getTaskId().equals("task1"))
                .findFirst().orElseThrow();
        assertThat(n1.getTotal()).isEqualTo(1);
        assertThat(n1.getCompleted()).isEqualTo(1);
        assertThat(n1.getMessage()).contains("Metadata fetch completed! 0 books need review.");

        MetadataBatchProgressNotification n2 = notifications.stream()
                .filter(n -> n.getTaskId().equals("task2"))
                .findFirst().orElseThrow();
        assertThat(n2.getTotal()).isEqualTo(1);
        assertThat(n2.getCompleted()).isEqualTo(0);
        assertThat(n2.getMessage()).contains("Metadata fetch completed! 1 books need review.");

        verify(metadataFetchTaskRepository).findAllWithProposals();
    }

    @Test
    void getActiveTasks_shouldFilterOutTasksWithNoRemainingProposals() {
        MetadataFetchProposalRow p1 = proposalRow(1L, "task1", FetchedMetadataProposalStatus.REJECTED);
        MetadataFetchJobRow job = new MetadataFetchJobRow(
                "task1", 1L, MetadataFetchTaskStatus.COMPLETED, null,
                FIXED_INSTANT, null, 1, 1, List.of(p1));

        when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(job));

        List<MetadataBatchProgressNotification> notifications = service.getActiveTasks();

        assertThat(notifications).isEmpty();
        verify(metadataFetchTaskRepository).findAllWithProposals();
    }
}
