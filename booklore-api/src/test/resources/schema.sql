-- Tables whose JPA @Entity has been removed during the jOOQ migration.
-- The H2 unit-test schema is generated from @Entity classes, so once an entity is
-- deleted its table must be recreated here for jOOQ repositories exercised by the
-- full Spring context (e.g. the ApplicationReadyEvent task scheduler bootstrap).
-- Kept intentionally empty: no rows means no real tasks get scheduled during tests.
CREATE TABLE IF NOT EXISTS task_cron_configuration
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type       VARCHAR(100) NOT NULL,
    cron_expression VARCHAR(100),
    enabled         TINYINT      NOT NULL DEFAULT 0,
    created_by      BIGINT       NOT NULL DEFAULT -1,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT uq_task_type UNIQUE (task_type)
);

-- Task history (`tasks`): read/written by TaskHistoryService now that its entity is gone.
CREATE TABLE IF NOT EXISTS tasks
(
    id                  VARCHAR(36) NOT NULL PRIMARY KEY,
    type                VARCHAR(50) NOT NULL,
    status              VARCHAR(50) NOT NULL,
    user_id             BIGINT      NOT NULL,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    progress_percentage INT,
    message             TEXT,
    errorDetails        TEXT,
    task_options        TEXT
);

-- Written by AuditService during full-context tests (login, etc.); the write swallows
-- errors, but recreating the table keeps audit writes working as they did under JPA.
CREATE TABLE IF NOT EXISTS audit_log
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT,
    username     VARCHAR(255)  NOT NULL,
    action       VARCHAR(100)  NOT NULL,
    entity_type  VARCHAR(100),
    entity_id    BIGINT,
    description  VARCHAR(1024) NOT NULL,
    ip_address   VARCHAR(45),
    country_code VARCHAR(2),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Read/written by AppMigrationStartup at ApplicationReadyEvent (existsByKey + insert).
CREATE TABLE IF NOT EXISTS app_migration
(
    migration_key VARCHAR(100) PRIMARY KEY,
    executed_at   TIMESTAMP NOT NULL,
    description   TEXT
);

-- Read app-wide by AppSettingService (getSettingsMap/findByName) once its entity was
-- dropped for jOOQ; the full Spring context loads settings during boot. Empty by
-- default so tests fall back to built-in defaults, exactly like a fresh install.
CREATE TABLE IF NOT EXISTS app_settings
(
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    val  TEXT
);

-- Scanned at boot by BookdropMonitoringService.@PostConstruct (findAllFilePathsIn) once its
-- entity was dropped for jOOQ; a full Spring context can reach that query if the bookdrop
-- folder contains files. Empty by default so the scan tracks nothing, exactly like a fresh
-- install. No UNIQUE on file_path: H2 cannot index a LOB/TEXT column and the table stays empty.
CREATE TABLE IF NOT EXISTS bookdrop_file
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_path         TEXT         NOT NULL,
    file_name         VARCHAR(512) NOT NULL,
    file_size         BIGINT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW',
    original_metadata TEXT,
    fetched_metadata  TEXT,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);

-- Read at boot by MigrateProgressToFileProgressMigration (ApplicationReadyEvent) once its entity was
-- dropped for jOOQ; the jOOQ repo joins book_file (kept as an entity). Empty by default, so a
-- full-context boot with no legacy progress rows touches nothing. No FK needed for the empty bridge.
CREATE TABLE IF NOT EXISTS user_book_file_progress
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    book_file_id     BIGINT       NOT NULL,
    position_data    VARCHAR(1000),
    position_href    VARCHAR(1000),
    progress_percent FLOAT,
    tts_position_cfi VARCHAR(1000),
    last_read_time   TIMESTAMP,
    CONSTRAINT uq_user_book_file UNIQUE (user_id, book_file_id)
);
