-- tasks module: a task change made during a meeting points to a meeting of the task's own team.
-- task_change gets the task's team_id, so composite foreign keys can check both links, as in V3.
-- They replace the single-column foreign keys on task_id and meeting_id.

ALTER TABLE task ADD UNIQUE (id, team_id);

ALTER TABLE task_change ADD COLUMN team_id uuid;
UPDATE task_change SET team_id = task.team_id FROM task WHERE task.id = task_change.task_id;
ALTER TABLE task_change ALTER COLUMN team_id SET NOT NULL;

ALTER TABLE task_change
  DROP CONSTRAINT task_change_task_id_fkey,
  DROP CONSTRAINT task_change_meeting_id_fkey,
  ADD FOREIGN KEY (task_id, team_id) REFERENCES task (id, team_id),
  -- Not checked when meeting_id is null (a change made outside a meeting).
  ADD FOREIGN KEY (meeting_id, team_id) REFERENCES meeting (id, team_id);
