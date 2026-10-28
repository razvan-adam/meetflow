-- tasks module: tasks (commitments), their history and their review in meetings. See docs/data-model.md.

CREATE TABLE task (
    id             uuid        PRIMARY KEY,
    team_id        uuid        NOT NULL,
    series_id      uuid        NOT NULL,
    meeting_id     uuid        NOT NULL,
    decision_id    uuid,
    agenda_item_id uuid,
    description    text        NOT NULL CHECK (char_length(description) <= 500),
    owner_id       uuid        NOT NULL REFERENCES app_user (id),
    deadline       date        NOT NULL,
    status         text        NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE')),
    created_by     uuid        NOT NULL REFERENCES app_user (id),
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    deleted_at     timestamptz,
    deleted_by     uuid        REFERENCES app_user (id),
    version        bigint      NOT NULL,
    UNIQUE (id, series_id),
    -- The task carries over in the series of the meeting where it was created, in that series' team.
    FOREIGN KEY (meeting_id, series_id) REFERENCES meeting (id, series_id),
    FOREIGN KEY (series_id, team_id) REFERENCES meeting_series (id, team_id),
    -- A task comes from a decision or an agenda item of its own meeting.
    FOREIGN KEY (decision_id, meeting_id) REFERENCES decision (id, meeting_id),
    FOREIGN KEY (agenda_item_id, meeting_id) REFERENCES agenda_item (id, meeting_id),
    CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

-- Pending-tasks dashboard and carry-over.
CREATE INDEX task_owner_status_deadline_idx ON task (owner_id, status, deadline);
CREATE INDEX task_series_status_idx ON task (series_id, status);
CREATE INDEX task_meeting_idx ON task (meeting_id);

-- One row per change, including the creation. Rows are only inserted, never updated or deleted.
CREATE TABLE task_change (
    id           uuid        PRIMARY KEY,
    task_id      uuid        NOT NULL REFERENCES task (id),
    changed_by   uuid        NOT NULL REFERENCES app_user (id),
    changed_at   timestamptz NOT NULL,
    meeting_id   uuid        REFERENCES meeting (id),
    old_status   text        CHECK (old_status IN ('OPEN', 'IN_PROGRESS', 'DONE')),
    new_status   text        CHECK (new_status IN ('OPEN', 'IN_PROGRESS', 'DONE')),
    old_owner_id uuid        REFERENCES app_user (id),
    new_owner_id uuid        REFERENCES app_user (id),
    old_deadline date,
    new_deadline date
);

CREATE INDEX task_change_task_idx ON task_change (task_id, changed_at);

-- The carried-over tasks reviewed in a meeting. Deleted with a (scheduled) meeting.
-- series_id keeps the task and the meeting in the same series.
CREATE TABLE meeting_task_review (
    meeting_id      uuid NOT NULL,
    task_id         uuid NOT NULL,
    series_id       uuid NOT NULL,
    status_at_close text CHECK (status_at_close IN ('OPEN', 'IN_PROGRESS', 'DONE')),
    PRIMARY KEY (meeting_id, task_id),
    FOREIGN KEY (meeting_id, series_id) REFERENCES meeting (id, series_id) ON DELETE CASCADE,
    FOREIGN KEY (task_id, series_id) REFERENCES task (id, series_id)
);

CREATE INDEX meeting_task_review_task_idx ON meeting_task_review (task_id);
