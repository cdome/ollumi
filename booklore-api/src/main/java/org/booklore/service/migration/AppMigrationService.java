package org.booklore.service.migration;

import org.booklore.repository.jooq.JooqAppMigrationRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@AllArgsConstructor
@Service
public class AppMigrationService {

    private JooqAppMigrationRepository migrationRepository;


    @Transactional
    public void executeMigration(Migration migration) {
        if (migrationRepository.existsByKey(migration.getKey())) {
            log.debug("Migration '{}' already executed, skipping", migration.getKey());
            return;
        }
        try {
            migration.execute();
            migrationRepository.insert(migration.getKey(), LocalDateTime.now(), migration.getDescription());

            log.info("Migration '{}' completed successfully", migration.getKey());
        } catch (Exception e) {
            log.error("Migration '{}' failed", migration.getKey(), e);
            throw e;
        }
    }
}
