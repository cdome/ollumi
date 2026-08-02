package org.booklore.service.migration.migrations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.repository.jooq.JooqAppSettingsRepository;
import org.booklore.repository.jooq.dto.AppSetting;
import org.booklore.service.migration.Migration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class MigrateInstallationIdToJsonMigration implements Migration {

    private static final String INSTALLATION_ID_KEY = "installation_id";

    private final JooqAppSettingsRepository appSettingsRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getKey() {
        return "migrateInstallationIdToJson";
    }

    @Override
    public String getDescription() {
        return "Migrate existing installation_id from plain string to JSON format with date";
    }

    @Override
    public void execute() {
        log.info("Executing migration: {}", getKey());

        AppSetting setting = appSettingsRepository.findByName(INSTALLATION_ID_KEY);

        if (setting != null) {
            String value = setting.getValue();
            try {
                objectMapper.readTree(value);
                log.info("Installation ID is already in JSON format, skipping migration");
            } catch (Exception e) {
                Instant now = Instant.now();
                String json = String.format("{\"id\":\"%s\",\"date\":\"%s\"}", value, now);
                appSettingsRepository.upsertByName(INSTALLATION_ID_KEY, json);
                log.info("Migrated installation ID to JSON format with current date");
            }
        }

        log.info("Completed migration: {}", getKey());
    }
}

