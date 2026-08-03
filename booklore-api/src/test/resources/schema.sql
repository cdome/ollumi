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

-- Queried on the book read path by ContentRestrictionService.filterByContent (applyRestrictions/
-- applyRestrictionsToDtos) once its entity was dropped for jOOQ; full-context tests reach that query
-- whenever they read books for a user. Empty by default so no book is filtered (no restrictions),
-- exactly like a fresh install. FK mirrors prod's ON DELETE CASCADE (V111).
-- NOTE: `value` is a reserved keyword in H2 (MariaDB tolerates it unquoted), so it must be
-- quoted here. jOOQ renders identifiers quoted-lowercase, so the quoted column still matches.
CREATE TABLE IF NOT EXISTS user_content_restriction
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    restriction_type VARCHAR(20)  NOT NULL,
    mode             VARCHAR(15)  NOT NULL,
    "value"          VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ucr_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_restriction UNIQUE (user_id, restriction_type, "value")
);

-- Read on the book read path by ReadingProgressService/BookService/AppBookService (findByUserIdAndBookIdIn
-- to enrich Book DTOs) once its entity was dropped for jOOQ; full-context tests reach it whenever they read
-- books. Empty by default so no progress is enriched, exactly like a fresh install. FKs mirror prod's
-- ON DELETE CASCADE (V1) so user/book deletion cascades in full-context tests too.
CREATE TABLE IF NOT EXISTS user_book_progress
(
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT NOT NULL,
    book_id                     BIGINT NOT NULL,
    last_read_time              TIMESTAMP,
    pdf_progress                INT,
    pdf_progress_percent        DOUBLE,
    epub_progress               VARCHAR(1000),
    epub_progress_href          VARCHAR(1000),
    epub_progress_percent       DOUBLE,
    cbx_progress                INT,
    cbx_progress_percent        DOUBLE,
    koreader_progress           VARCHAR(1000),
    koreader_progress_percent   DOUBLE,
    koreader_device             VARCHAR(100),
    koreader_device_id          VARCHAR(100),
    kobo_progress_percent       DOUBLE,
    kobo_location               VARCHAR(1000),
    kobo_location_type          VARCHAR(50),
    kobo_location_source        VARCHAR(512),
    read_status                 VARCHAR(50),
    date_finished               TIMESTAMP,
    koreader_last_sync_time     TIMESTAMP,
    kobo_progress_received_time TIMESTAMP,
    kobo_status_sent_time       TIMESTAMP,
    kobo_progress_sent_time     TIMESTAMP,
    read_status_modified_time   TIMESTAMP,
    personal_rating             TINYINT,
    CONSTRAINT fk_ubp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ubp_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE
);

-- Counted at boot-adjacent telemetry / used by KOReader auth once its entity was dropped for jOOQ.
-- FK mirrors prod's ON DELETE CASCADE (V134) so user-deletion cascade holds in full-context tests too.
CREATE TABLE IF NOT EXISTS koreader_user
(
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    username                  VARCHAR(100) NOT NULL,
    password                  VARCHAR(255) NOT NULL,
    password_md5              VARCHAR(255) NOT NULL,
    created_at                TIMESTAMP,
    updated_at                TIMESTAMP,
    sync_enabled              TINYINT      NOT NULL DEFAULT 0,
    sync_with_booklore_reader TINYINT      NOT NULL DEFAULT 0,
    booklore_user_id          BIGINT,
    CONSTRAINT fk_koreader_booklore_user FOREIGN KEY (booklore_user_id) REFERENCES users (id) ON DELETE CASCADE
);
