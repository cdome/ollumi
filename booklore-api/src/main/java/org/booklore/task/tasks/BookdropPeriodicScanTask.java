package org.booklore.task.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.model.enums.UserPermission;
import org.booklore.service.bookdrop.BookdropMonitoringService;
import org.booklore.task.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookdropPeriodicScanTask implements Task {

    private final BookdropMonitoringService bookdropMonitoringService;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        if (!UserPermission.CAN_ACCESS_TASK_MANAGER.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(UserPermission.CAN_ACCESS_TASK_MANAGER);
        }
    }

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        String taskId = UUID.randomUUID().toString();
        TaskType taskType = getTaskType();
        TaskStatus status;

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started", getTaskType());

        try {
            bookdropMonitoringService.rescanBookdropFolder();
            status = TaskStatus.COMPLETED;
        } catch (Exception e) {
            log.error("{}: Error scanning bookdrop ingest folder", getTaskType(), e);
            status = TaskStatus.FAILED;
        }

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return new TaskCreateResponse(taskId, taskType, status);
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.BOOKDROP_PERIODIC_SCANNING;
    }
}
