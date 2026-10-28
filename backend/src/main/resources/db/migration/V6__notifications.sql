-- notifications module: sent emails and reliable event delivery. See docs/data-model.md.

-- A record of the emails sent, so none is sent twice. Email bodies are not stored.
CREATE TABLE email_delivery (
    id                  uuid        PRIMARY KEY,
    kind                text        NOT NULL CHECK (kind IN ('INVITATION', 'MINUTES', 'TASK_SUMMARY')),
    reference_id        uuid        NOT NULL,
    recipient_email     text        NOT NULL,
    sent_at             timestamptz NOT NULL,
    provider_message_id text,
    UNIQUE (kind, reference_id, recipient_email)
);

-- Rows are deleted after 90 days.
CREATE INDEX email_delivery_sent_at_idx ON email_delivery (sent_at);

-- Spring Modulith event publication registry, copied from spring-modulith-events-jdbc's
-- schemas/v2/schema-postgresql.sql so Flyway owns it. Events are retried until their listener succeeds.
CREATE TABLE event_publication (
    id                     uuid        NOT NULL,
    listener_id            text        NOT NULL,
    event_type             text        NOT NULL,
    serialized_event       text        NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamptz,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
