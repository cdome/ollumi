package org.booklore.service.metadata;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.FetchedProposalMapper;
import org.booklore.model.dto.FetchedProposal;
import org.booklore.model.dto.MetadataBatchProgressNotification;
import org.booklore.model.dto.MetadataFetchTask;
import org.booklore.model.dto.response.MetadataTaskDetailsResponse;
import org.booklore.model.enums.FetchedMetadataProposalStatus;
import org.booklore.model.enums.MetadataFetchTaskStatus;
import org.booklore.repository.jooq.JooqMetadataFetchJobRepository;
import org.booklore.repository.jooq.dto.MetadataFetchJobRow;
import org.booklore.repository.jooq.dto.MetadataFetchProposalRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MetadataTaskService {

    private final JooqMetadataFetchJobRepository metadataFetchTaskRepository;
    private final FetchedProposalMapper fetchedProposalMapper;
    private final AuthenticationService authenticationService;

    public Optional<MetadataTaskDetailsResponse> getTaskWithProposals(String taskId) {
        return metadataFetchTaskRepository.findById(taskId)
                .map(this::buildTaskDetailsResponse);
    }

    private MetadataTaskDetailsResponse buildTaskDetailsResponse(MetadataFetchJobRow task) {
        List<FetchedProposal> proposals = task.getProposals().stream()
                .filter(p -> p.getStatus() == FetchedMetadataProposalStatus.FETCHED)
                .map(fetchedProposalMapper::toDto)
                .toList();

        MetadataFetchTask taskDto = MetadataFetchTask.builder()
                .id(task.getTaskId())
                .status(task.getStatus())
                .completed(task.getCompletedBooks())
                .totalBooks(task.getTotalBooksCount())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .initiatedBy(task.getUserId())
                .proposals(proposals)
                .build();

        return new MetadataTaskDetailsResponse(taskDto);
    }

    @Transactional
    public boolean deleteTaskAndProposals(String taskId) {
        return metadataFetchTaskRepository.deleteById(taskId);
    }

    public boolean updateProposalStatus(String taskId, Long proposalId, String statusStr) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        Optional<FetchedMetadataProposalStatus> statusOpt = parseStatus(statusStr);
        return statusOpt.map(fetchedMetadataProposalStatus -> metadataFetchTaskRepository.findProposalById(proposalId)
                .filter(p -> taskId.equals(p.getTaskId()))
                .map(proposal -> {
                    metadataFetchTaskRepository.updateProposalReview(proposalId, fetchedMetadataProposalStatus, Instant.now(), userId);
                    return true;
                })
                .orElse(false)).orElse(false);

    }

    private Optional<FetchedMetadataProposalStatus> parseStatus(String statusStr) {
        try {
            return Optional.of(FetchedMetadataProposalStatus.valueOf(statusStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public List<MetadataBatchProgressNotification> getActiveTasks() {
        List<MetadataFetchJobRow> tasks = metadataFetchTaskRepository.findAllWithProposals();

        return tasks.stream()
                .filter(task -> task.getStatus() == MetadataFetchTaskStatus.COMPLETED || task.getStatus() == MetadataFetchTaskStatus.ERROR)
                .map(task -> {
                    List<MetadataFetchProposalRow> proposals = task.getProposals();
                    List<MetadataFetchProposalRow> remaining = proposals.stream()
                            .filter(p -> p.getStatus() != FetchedMetadataProposalStatus.REJECTED)
                            .toList();

                    int total;
                    long acceptedCount = remaining.stream()
                            .filter(p -> p.getStatus() == FetchedMetadataProposalStatus.ACCEPTED)
                            .count();
                    long fetchedCount = remaining.stream()
                            .filter(p -> p.getStatus() == FetchedMetadataProposalStatus.FETCHED)
                            .count();

                    String message;
                    String status;
                    int completedCount = task.getCompletedBooks() != null ? task.getCompletedBooks() : 0;

                    if (task.getStatus() == MetadataFetchTaskStatus.ERROR) {
                        total = task.getTotalBooksCount() != null ? task.getTotalBooksCount() : remaining.size();
                        message = String.format("Metadata fetch failed, processed %d of %d books.", completedCount, total);
                        status = "ERROR";
                    } else {
                        total = remaining.size();
                        message = String.format("Metadata fetch completed! %d books need review.", fetchedCount);
                        status = "COMPLETED";
                        completedCount = (int) acceptedCount;
                    }

                    return new MetadataBatchProgressNotification(
                            task.getTaskId(),
                            completedCount,
                            total,
                            message,
                            status,
                            true
                    );
                })
                .filter(n -> n.getTotal() > 0)
                .toList();
    }
}
