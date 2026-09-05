package org.booklore.service.task;

import org.booklore.repository.jooq.JooqTaskHistoryRepository;
import org.booklore.repository.jooq.dto.TaskHistory;
import org.booklore.task.TaskStatus;
import org.booklore.model.dto.response.TasksHistoryResponse;
import org.booklore.model.enums.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskHistoryService {

    private final JooqTaskHistoryRepository taskHistoryRepository;
    private final AuditService auditService;

    @Transactional
    public void createTask(String taskId, TaskType type, Long userId, Map<String, Object> options) {
        taskHistoryRepository.insert(taskId, type, TaskStatus.ACCEPTED, userId, LocalDateTime.now(), 0, options);
        auditService.log(AuditAction.TASK_EXECUTED, "Task", null, buildTaskDescription(type, options));
    }

    private static final int MAX_DESCRIPTION_LENGTH = 1024;

    private String buildTaskDescription(TaskType type, Map<String, Object> options) {
        String taskName = type != null ? type.getName() : "Unknown";
        StringBuilder sb = new StringBuilder("Started task: ").append(taskName);
        if (options == null || options.isEmpty()) {
            return sb.toString();
        }
        Object bookIds = options.get("bookIds");
        Object libraryId = options.get("libraryId");
        if (bookIds instanceof Collection<?> ids && !ids.isEmpty()) {
            sb.append(" (").append(ids.size()).append(" books, IDs: ");
            String truncationSuffix = "...)";
            Iterator<?> it = ids.iterator();
            boolean truncated = false;
            while (it.hasNext()) {
                String id = it.next().toString();
                boolean isLast = !it.hasNext();
                String separator = sb.charAt(sb.length() - 1) == ' ' ? "" : ", ";
                if (isLast && sb.length() + separator.length() + id.length() + 1 <= MAX_DESCRIPTION_LENGTH) {
                    sb.append(separator).append(id).append(")");
                } else if (!isLast && sb.length() + separator.length() + id.length() + truncationSuffix.length() <= MAX_DESCRIPTION_LENGTH) {
                    sb.append(separator).append(id);
                } else {
                    truncated = true;
                    break;
                }
            }
            if (truncated) {
                sb.append(truncationSuffix);
            }
        } else if (libraryId != null) {
            sb.append(" (Library ID: ").append(libraryId).append(")");
        }
        return sb.toString();
    }

    @Transactional
    public void updateTaskStatus(String taskId, TaskStatus status, String message) {
        LocalDateTime now = LocalDateTime.now();
        if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
            taskHistoryRepository.completeStatus(taskId, status, message, now, now, 100);
        } else {
            taskHistoryRepository.updateStatus(taskId, status, message, now);
        }
    }

    @Transactional
    public void updateTaskError(String taskId, String errorDetails) {
        LocalDateTime now = LocalDateTime.now();
        taskHistoryRepository.updateError(taskId, errorDetails, now, now);
        log.error("Task failed: id={}", taskId);
    }

    @Transactional(readOnly = true)
    public TasksHistoryResponse getLatestTasksForEachType() {
        List<TaskHistory> latestTasks;
        try {
            latestTasks = taskHistoryRepository.findLatestTaskForEachType();
        } catch (Exception e) {
            log.warn("Error fetching latest tasks, possibly due to removed enum values: {}", e.getMessage());
            latestTasks = Collections.emptyList();
        }

        Map<TaskType, TaskHistory> taskHistoryMap = latestTasks.stream()
                .collect(Collectors.toMap(TaskHistory::getType, task -> task, (existing, replacement) -> existing));

        List<TasksHistoryResponse.TaskHistory> allTasks = new ArrayList<>();

        for (TaskType taskType : TaskType.values()) {
            if (taskType.isHiddenFromUI()) {
                continue;
            }

            TaskHistory existingTask = taskHistoryMap.get(taskType);

            if (existingTask != null) {
                allTasks.add(mapToTaskInfo(existingTask));
            } else {
                allTasks.add(createMetadataOnlyTaskInfo(taskType));
            }
        }

        return TasksHistoryResponse.builder()
                .taskHistories(allTasks)
                .build();
    }

    private TasksHistoryResponse.TaskHistory mapToTaskInfo(TaskHistory task) {
        return TasksHistoryResponse.TaskHistory.builder()
                .id(task.getId())
                .type(task.getType())
                .status(task.getStatus())
                .progressPercentage(task.getProgressPercentage())
                .message(task.getMessage())
                .createdAt(toUtcInstant(task.getCreatedAt()))
                .updatedAt(toUtcInstant(task.getUpdatedAt()))
                .completedAt(toUtcInstant(task.getCompletedAt()))
                .build();
    }

    private Instant toUtcInstant(LocalDateTime localDateTime) {
        return localDateTime != null ? localDateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    private TasksHistoryResponse.TaskHistory createMetadataOnlyTaskInfo(TaskType taskType) {
        return TasksHistoryResponse.TaskHistory.builder()
                .id(null)
                .type(taskType)
                .status(null)
                .progressPercentage(null)
                .message(null)
                .createdAt(null)
                .updatedAt(null)
                .completedAt(null)
                .build();
    }
}
