package org.booklore.task.tasks;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.model.enums.UserPermission;
import org.booklore.repository.jooq.JooqBookRepository;
import org.booklore.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeletedBooksCleanupTask implements Task {

    private final JooqBookRepository jooqBookRepository;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        if (!UserPermission.CAN_ACCESS_TASK_MANAGER.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(UserPermission.CAN_ACCESS_TASK_MANAGER);
        }
    }

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        TaskCreateResponse.TaskCreateResponseBuilder builder = TaskCreateResponse.builder()
                .taskId(UUID.randomUUID().toString())
                .taskType(getTaskType());

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started", getTaskType());

        try {
            int deletedCount;
            if (request.isTriggeredByCron()) {
                Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
                deletedCount = jooqBookRepository.deleteSoftDeletedBefore(cutoff);
                log.info("{}: Removed {} deleted books older than {}", getTaskType(), deletedCount, cutoff);
            } else {
                deletedCount = jooqBookRepository.deleteAllSoftDeleted();
                log.info("{}: Removed all {} deleted books (on-demand execution)", getTaskType(), deletedCount);
            }
            builder.status(TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("{}: Error cleaning up deleted books", getTaskType(), e);
            builder.status(TaskStatus.FAILED);
        }

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return builder.build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.CLEANUP_DELETED_BOOKS;
    }

    @Override
    public String getMetadata() {
        long deleted = jooqBookRepository.countSoftDeleted();
        return "Book" + (deleted != 1 ? "s" : "") + " pending cleanup: " + deleted;
    }
}