-- koreader_user was cascade-deleted with its owning BookLoreUser via JPA @OneToOne(cascade=ALL).
-- That entity is being dropped for jOOQ, so make the FK cascade at the DB level to preserve
-- user-deletion behavior.
ALTER TABLE koreader_user
    DROP FOREIGN KEY fk_booklore_user;
ALTER TABLE koreader_user
    ADD CONSTRAINT fk_booklore_user FOREIGN KEY (booklore_user_id) REFERENCES users (id) ON DELETE CASCADE;
