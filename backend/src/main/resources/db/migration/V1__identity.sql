-- identity module: users and login sessions. See docs/data-model.md.

CREATE TABLE app_user (
    id            uuid        PRIMARY KEY,
    email         text        UNIQUE CHECK (email = lower(email) AND char_length(email) <= 254),
    name          text        NOT NULL CHECK (char_length(name) <= 100),
    password_hash text,
    created_at    timestamptz NOT NULL,
    deleted_at    timestamptz,
    -- An active account has an email and a password; a deleted (anonymized) one has neither.
    CHECK (deleted_at IS NOT NULL OR (email IS NOT NULL AND password_hash IS NOT NULL)),
    CHECK (deleted_at IS NULL OR (email IS NULL AND password_hash IS NULL))
);

-- Spring Session JDBC tables, copied from spring-session-jdbc's schema-postgresql.sql
-- so Flyway owns them (spring.session.jdbc.initialize-schema=never). principal_name is widened
-- from varchar(100) to fit any email address that app_user allows.
CREATE TABLE spring_session (
    primary_id            char(36)     NOT NULL,
    session_id            char(36)     NOT NULL,
    creation_time         bigint       NOT NULL,
    last_access_time      bigint       NOT NULL,
    max_inactive_interval int          NOT NULL,
    expiry_time           bigint       NOT NULL,
    principal_name        varchar(254),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id char(36)     NOT NULL,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
