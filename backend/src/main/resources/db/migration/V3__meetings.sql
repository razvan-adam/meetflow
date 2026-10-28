-- meetings module: series, meetings, participants, agenda items and decisions. See docs/data-model.md.
-- Composite foreign keys keep every link inside one team: children reference their parent by
-- (id, team_id), and links between a meeting's contents also carry its meeting_id.

CREATE TABLE meeting_series (
    id            uuid        PRIMARY KEY,
    team_id       uuid        NOT NULL REFERENCES team (id),
    name          text        NOT NULL CHECK (char_length(name) <= 100),
    organizer_id  uuid        NOT NULL REFERENCES app_user (id),
    created_at    timestamptz NOT NULL,
    -- 'simple' configuration: customers may write in any language.
    search_vector tsvector    GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, name)) STORED,
    UNIQUE (id, team_id)
);

CREATE INDEX meeting_series_team_idx ON meeting_series (team_id);
CREATE INDEX meeting_series_search_idx ON meeting_series USING gin (search_vector);

CREATE TABLE meeting (
    id           uuid        PRIMARY KEY,
    team_id      uuid        NOT NULL,
    series_id    uuid        NOT NULL,
    scheduled_at timestamptz NOT NULL,
    status       text        NOT NULL CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'CLOSED')),
    started_at   timestamptz,
    closed_at    timestamptz,
    closed_by    uuid        REFERENCES app_user (id),
    version      bigint      NOT NULL,
    UNIQUE (id, team_id),
    -- Lets tasks and task reviews stay in the series of their meeting.
    UNIQUE (id, series_id),
    FOREIGN KEY (series_id, team_id) REFERENCES meeting_series (id, team_id),
    -- The timestamps match the status: a meeting starts, then the organizer closes it.
    CHECK (status <> 'SCHEDULED' OR (started_at IS NULL AND closed_at IS NULL AND closed_by IS NULL)),
    CHECK (status <> 'IN_PROGRESS' OR (started_at IS NOT NULL AND closed_at IS NULL AND closed_by IS NULL)),
    CHECK (status <> 'CLOSED' OR (started_at IS NOT NULL AND closed_at IS NOT NULL AND closed_by IS NOT NULL))
);

-- At most one meeting per series that isn't closed, so "the previous meeting" for carry-over
-- is always the latest closed one.
CREATE UNIQUE INDEX meeting_one_open_per_series_idx ON meeting (series_id) WHERE status <> 'CLOSED';
CREATE INDEX meeting_series_scheduled_idx ON meeting (series_id, scheduled_at);
CREATE INDEX meeting_team_idx ON meeting (team_id);

-- Children of a meeting are deleted with it; only a SCHEDULED meeting can be deleted (service check).
CREATE TABLE meeting_participant (
    meeting_id uuid NOT NULL REFERENCES meeting (id) ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES app_user (id),
    PRIMARY KEY (meeting_id, user_id)
);

CREATE INDEX meeting_participant_user_idx ON meeting_participant (user_id);

CREATE TABLE agenda_item (
    id                   uuid   PRIMARY KEY,
    team_id              uuid   NOT NULL,
    meeting_id           uuid   NOT NULL,
    position             int    NOT NULL,
    title                text   NOT NULL CHECK (char_length(title) <= 200),
    notes                text   CHECK (char_length(notes) <= 10000),
    status               text   NOT NULL CHECK (status IN ('PENDING', 'COVERED', 'NOT_COVERED')),
    carried_over_from_id uuid,
    version              bigint NOT NULL,
    UNIQUE (id, team_id),
    UNIQUE (id, meeting_id),
    FOREIGN KEY (meeting_id, team_id) REFERENCES meeting (id, team_id) ON DELETE CASCADE,
    FOREIGN KEY (carried_over_from_id, team_id) REFERENCES agenda_item (id, team_id),
    -- Deferred so items can be reordered by swapping positions in one transaction.
    CONSTRAINT agenda_item_position_key UNIQUE (meeting_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE decision (
    id             uuid        PRIMARY KEY,
    team_id        uuid        NOT NULL,
    meeting_id     uuid        NOT NULL,
    agenda_item_id uuid,
    text           text        NOT NULL CHECK (char_length(text) <= 2000),
    created_by     uuid        NOT NULL REFERENCES app_user (id),
    created_at     timestamptz NOT NULL,
    search_vector  tsvector    GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, text)) STORED,
    UNIQUE (id, meeting_id),
    FOREIGN KEY (meeting_id, team_id) REFERENCES meeting (id, team_id),
    -- A decision is about an agenda item of its own meeting.
    FOREIGN KEY (agenda_item_id, meeting_id) REFERENCES agenda_item (id, meeting_id)
);

CREATE INDEX decision_meeting_idx ON decision (meeting_id);
CREATE INDEX decision_search_idx ON decision USING gin (search_vector);
