package org.booklore.service.metadata;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.FetchedProposalMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.FetchedProposal;
import org.booklore.model.dto.MetadataBatchProgressNotification;
import org.booklore.model.dto.response.MetadataTaskDetailsResponse;
import org.booklore.model.enums.FetchedMetadataProposalStatus;
import org.booklore.model.enums.MetadataFetchTaskStatus;
import org.booklore.repository.jooq.JooqMetadataFetchJobRepository;
import org.booklore.repository.jooq.dto.MetadataFetchJobRow;
import org.booklore.repository.jooq.dto.MetadataFetchProposalRow;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataTaskServiceTest {

    @Mock
    private JooqMetadataFetchJobRepository metadataFetchTaskRepository;

    @Mock
    private FetchedProposalMapper fetchedProposalMapper;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private MetadataTaskService service;

    private MetadataFetchJobRow buildTask(String taskId, MetadataFetchTaskStatus status, List<MetadataFetchProposalRow> proposals) {
        return new MetadataFetchJobRow(
                taskId,
                1L,
                status,
                null,
                Instant.now(),
                null,
                10,
                5,
                proposals
        );
    }

    private MetadataFetchProposalRow buildProposal(Long id, String taskId, FetchedMetadataProposalStatus status) {
        return new MetadataFetchProposalRow(
                id,
                taskId,
                100L,
                null,
                null,
                null,
                status,
                "{}"
        );
    }

    @Nested
    class GetTaskWithProposals {

        @Test
        void returnsEmptyWhenTaskNotFound() {
            when(metadataFetchTaskRepository.findById("missing")).thenReturn(Optional.empty());
            assertThat(service.getTaskWithProposals("missing")).isEmpty();
        }

        @Test
        void returnsResponseWithOnlyFetchedProposals() {
            MetadataFetchProposalRow fetched = buildProposal(1L, "t1", FetchedMetadataProposalStatus.FETCHED);
            MetadataFetchProposalRow accepted = buildProposal(2L, "t1", FetchedMetadataProposalStatus.ACCEPTED);
            MetadataFetchProposalRow rejected = buildProposal(3L, "t1", FetchedMetadataProposalStatus.REJECTED);
            MetadataFetchJobRow task = buildTask("t1", MetadataFetchTaskStatus.COMPLETED, List.of(fetched, accepted, rejected));

            when(metadataFetchTaskRepository.findById("t1")).thenReturn(Optional.of(task));
            when(fetchedProposalMapper.toDto(fetched)).thenReturn(FetchedProposal.builder().proposalId(1L).build());

            Optional<MetadataTaskDetailsResponse> result = service.getTaskWithProposals("t1");

            assertThat(result).isPresent();
            assertThat(result.get().getTask().getProposals()).hasSize(1);
            assertThat(result.get().getTask().getProposals().getFirst().getProposalId()).isEqualTo(1L);
            verify(fetchedProposalMapper, times(1)).toDto(any());
        }

        @Test
        void mapsTaskFieldsCorrectly() {
            MetadataFetchJobRow task = new MetadataFetchJobRow(
                    "t2",
                    1L,
                    MetadataFetchTaskStatus.IN_PROGRESS,
                    null,
                    Instant.now(),
                    Instant.now(),
                    10,
                    5,
                    List.of()
            );

            when(metadataFetchTaskRepository.findById("t2")).thenReturn(Optional.of(task));

            var result = service.getTaskWithProposals("t2").orElseThrow();
            var dto = result.getTask();

            assertThat(dto.getId()).isEqualTo("t2");
            assertThat(dto.getStatus()).isEqualTo(MetadataFetchTaskStatus.IN_PROGRESS);
            assertThat(dto.getCompleted()).isEqualTo(5);
            assertThat(dto.getTotalBooks()).isEqualTo(10);
            assertThat(dto.getInitiatedBy()).isEqualTo(1L);
        }
    }

    @Nested
    class DeleteTaskAndProposals {

        @Test
        void returnsTrueAndDeletesWhenFound() {
            when(metadataFetchTaskRepository.deleteById("t1")).thenReturn(true);

            assertThat(service.deleteTaskAndProposals("t1")).isTrue();
            verify(metadataFetchTaskRepository).deleteById("t1");
        }

        @Test
        void returnsFalseWhenNotFound() {
            when(metadataFetchTaskRepository.deleteById("missing")).thenReturn(false);
            assertThat(service.deleteTaskAndProposals("missing")).isFalse();
            verify(metadataFetchTaskRepository).deleteById("missing");
        }
    }

    @Nested
    class UpdateProposalStatus {

        @Test
        void updatesProposalStatusSuccessfully() {
            Long userId = 42L;
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(userId).build());

            MetadataFetchProposalRow proposal = buildProposal(10L, "t1", FetchedMetadataProposalStatus.FETCHED);
            when(metadataFetchTaskRepository.findProposalById(10L)).thenReturn(Optional.of(proposal));

            boolean result = service.updateProposalStatus("t1", 10L, "ACCEPTED");

            assertThat(result).isTrue();
            verify(metadataFetchTaskRepository).updateProposalReview(
                    eq(10L), eq(FetchedMetadataProposalStatus.ACCEPTED), any(Instant.class), eq(userId));
        }

        @Test
        void returnsFalseForInvalidStatus() {
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(1L).build());

            boolean result = service.updateProposalStatus("t1", 10L, "INVALID_STATUS");

            assertThat(result).isFalse();
            verify(metadataFetchTaskRepository, never())
                    .updateProposalReview(anyLong(), any(), any(Instant.class), anyLong());
        }

        @Test
        void returnsFalseWhenProposalNotFound() {
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(1L).build());
            when(metadataFetchTaskRepository.findProposalById(99L)).thenReturn(Optional.empty());

            boolean result = service.updateProposalStatus("t1", 99L, "ACCEPTED");

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalseWhenProposalTaskIdMismatch() {
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(1L).build());

            MetadataFetchProposalRow proposal = buildProposal(10L, "other-task", FetchedMetadataProposalStatus.FETCHED);
            when(metadataFetchTaskRepository.findProposalById(10L)).thenReturn(Optional.of(proposal));

            boolean result = service.updateProposalStatus("t1", 10L, "ACCEPTED");

            assertThat(result).isFalse();
            verify(metadataFetchTaskRepository, never())
                    .updateProposalReview(anyLong(), any(), any(Instant.class), anyLong());
        }

        @Test
        void returnsFalseWhenProposalJobIsNull() {
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(1L).build());

            MetadataFetchProposalRow proposal = buildProposal(10L, "", FetchedMetadataProposalStatus.FETCHED);
            when(metadataFetchTaskRepository.findProposalById(10L)).thenReturn(Optional.of(proposal));

            boolean result = service.updateProposalStatus("t1", 10L, "ACCEPTED");

            assertThat(result).isFalse();
            verify(metadataFetchTaskRepository, never())
                    .updateProposalReview(anyLong(), any(), any(Instant.class), anyLong());
        }

        @Test
        void handlesLowercaseStatusString() {
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(1L).build());

            MetadataFetchProposalRow proposal = buildProposal(10L, "t1", FetchedMetadataProposalStatus.FETCHED);
            when(metadataFetchTaskRepository.findProposalById(10L)).thenReturn(Optional.of(proposal));

            boolean result = service.updateProposalStatus("t1", 10L, "rejected");

            assertThat(result).isTrue();
            verify(metadataFetchTaskRepository).updateProposalReview(
                    eq(10L), eq(FetchedMetadataProposalStatus.REJECTED), any(Instant.class), eq(1L));
        }
    }

    @Nested
    class GetActiveTasks {

        @Test
        void filtersOutInProgressAndCancelledTasks() {
            MetadataFetchJobRow inProgress = buildTask("ip", MetadataFetchTaskStatus.IN_PROGRESS, List.of());
            MetadataFetchJobRow cancelled = buildTask("ca", MetadataFetchTaskStatus.CANCELLED, List.of());
            MetadataFetchJobRow completed = buildTask("co", MetadataFetchTaskStatus.COMPLETED, List.of(
                    buildProposal(1L, "co", FetchedMetadataProposalStatus.FETCHED)
            ));

            when(metadataFetchTaskRepository.findAllWithProposals())
                    .thenReturn(List.of(inProgress, cancelled, completed));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getTaskId()).isEqualTo("co");
        }

        @Test
        void completedTaskCountsAcceptedAsCompleted() {
            MetadataFetchProposalRow accepted1 = buildProposal(1L, "co", FetchedMetadataProposalStatus.ACCEPTED);
            MetadataFetchProposalRow accepted2 = buildProposal(2L, "co", FetchedMetadataProposalStatus.ACCEPTED);
            MetadataFetchProposalRow fetched = buildProposal(3L, "co", FetchedMetadataProposalStatus.FETCHED);
            MetadataFetchProposalRow rejected = buildProposal(4L, "co", FetchedMetadataProposalStatus.REJECTED);

            MetadataFetchJobRow task = buildTask("co", MetadataFetchTaskStatus.COMPLETED,
                    List.of(accepted1, accepted2, fetched, rejected));

            when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(task));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).hasSize(1);
            var notification = result.getFirst();
            assertThat(notification.getCompleted()).isEqualTo(2);
            assertThat(notification.getTotal()).isEqualTo(3);
            assertThat(notification.getStatus()).isEqualTo("COMPLETED");
            assertThat(notification.getMessage()).contains("1 books need review");
        }

        @Test
        void errorTaskUsesTotalBooksCountAndCompletedBooks() {
            MetadataFetchProposalRow fetched = buildProposal(1L, "err", FetchedMetadataProposalStatus.FETCHED);

            MetadataFetchJobRow task = new MetadataFetchJobRow(
                    "err",
                    1L,
                    MetadataFetchTaskStatus.ERROR,
                    null,
                    Instant.now(),
                    null,
                    20,
                    15,
                    List.of(fetched)
            );

            when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(task));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).hasSize(1);
            var notification = result.getFirst();
            assertThat(notification.getCompleted()).isEqualTo(15);
            assertThat(notification.getTotal()).isEqualTo(20);
            assertThat(notification.getStatus()).isEqualTo("ERROR");
            assertThat(notification.getMessage()).contains("failed");
        }

        @Test
        void errorTaskFallsBackToRemainingSizeWhenTotalBooksCountNull() {
            MetadataFetchProposalRow fetched1 = buildProposal(1L, "err", FetchedMetadataProposalStatus.FETCHED);
            MetadataFetchProposalRow fetched2 = buildProposal(2L, "err", FetchedMetadataProposalStatus.FETCHED);

            MetadataFetchJobRow task = new MetadataFetchJobRow(
                    "err",
                    1L,
                    MetadataFetchTaskStatus.ERROR,
                    null,
                    Instant.now(),
                    null,
                    null,
                    null,
                    List.of(fetched1, fetched2)
            );

            when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(task));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getTotal()).isEqualTo(2);
            assertThat(result.getFirst().getCompleted()).isEqualTo(0);
        }

        @Test
        void filtersOutTasksWithZeroTotal() {
            MetadataFetchJobRow task = buildTask("empty", MetadataFetchTaskStatus.COMPLETED, List.of(
                    buildProposal(1L, "empty", FetchedMetadataProposalStatus.REJECTED)
            ));

            when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(task));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).isEmpty();
        }

        @Test
        void allNotificationsHaveIsReviewTrue() {
            MetadataFetchProposalRow fetched = buildProposal(1L, "t1", FetchedMetadataProposalStatus.FETCHED);

            MetadataFetchJobRow task = buildTask("t1", MetadataFetchTaskStatus.COMPLETED, List.of(fetched));

            when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(task));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).allMatch(MetadataBatchProgressNotification::isReview);
        }
    }
}
