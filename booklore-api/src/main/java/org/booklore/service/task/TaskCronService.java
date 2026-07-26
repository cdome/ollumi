package org.booklore.service.task;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCronConfigRequest;
import org.booklore.model.dto.response.CronConfig;
import org.booklore.model.enums.TaskType;
import org.booklore.repository.jooq.JooqTaskCronConfigurationRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
@AllArgsConstructor
public class TaskCronService {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private final JooqTaskCronConfigurationRepository repository;
    private final AuthenticationService authService;

    @Transactional(readOnly = true)
    public List<CronConfig> getAllEnabledCronConfigs() {
        return repository.findAllEnabled();
    }

    @Transactional(readOnly = true)
    public CronConfig getCronConfigOrDefault(TaskType taskType) {
        validateTaskTypeForCron(taskType);
        CronConfig existing = repository.findByTaskType(taskType);
        return existing != null ? existing
                : CronConfig.builder()
                        .taskType(taskType)
                        .enabled(false)
                        .build();
    }

    @Transactional
    public CronConfig patchCronConfig(TaskType taskType, TaskCronConfigRequest request) {
        validateTaskTypeForCron(taskType);
        BookLoreUser user = authService.getAuthenticatedUser();

        CronConfig existing = repository.findByTaskType(taskType);
        String cronExpression = existing != null ? existing.getCronExpression() : null;
        boolean enabled = existing != null && Boolean.TRUE.equals(existing.getEnabled());

        if (request.getCronExpression() != null) {
            validateCronExpression(request.getCronExpression());
            cronExpression = request.getCronExpression();
        }
        if (request.getEnabled() != null) {
            enabled = request.getEnabled();
        }

        CronConfig saved = repository.save(taskType, cronExpression, enabled, user.getId());
        log.info("Updated cron configuration for task type: {}", taskType);
        return saved;
    }

    private void validateTaskTypeForCron(TaskType taskType) {
        if (taskType == null) {
            throw new APIException("Task type is required", HttpStatus.BAD_REQUEST);
        }
        if (!taskType.isCronSupported()) {
            throw new APIException("Task type " + taskType + " does not support cron scheduling", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new APIException("Cron expression is required", HttpStatus.BAD_REQUEST);
        }
        String[] fields = WHITESPACE_PATTERN.split(cronExpression.trim());
        if (fields.length != 6) {
            throw new APIException("Invalid cron expression format. Expected 6 fields (second minute hour day month day-of-week)", HttpStatus.BAD_REQUEST);
        }
        try {
            new CronTrigger(cronExpression);
        } catch (Exception e) {
            throw new APIException("Invalid cron expression: " + cronExpression + ". Error: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

}
