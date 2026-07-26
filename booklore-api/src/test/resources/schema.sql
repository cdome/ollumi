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
