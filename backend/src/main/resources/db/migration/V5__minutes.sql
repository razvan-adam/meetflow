-- minutes module: one read-only snapshot per closed meeting. See docs/data-model.md.

CREATE TABLE minutes (
    id           uuid        PRIMARY KEY,
    team_id      uuid        NOT NULL,
    meeting_id   uuid        NOT NULL UNIQUE,
    generated_at timestamptz NOT NULL,
    content      jsonb       NOT NULL,
    FOREIGN KEY (meeting_id, team_id) REFERENCES meeting (id, team_id)
);

CREATE FUNCTION reject_minutes_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'minutes are read-only once generated';
END;
$$;

CREATE TRIGGER minutes_read_only
    BEFORE UPDATE ON minutes
    FOR EACH ROW EXECUTE FUNCTION reject_minutes_update();
