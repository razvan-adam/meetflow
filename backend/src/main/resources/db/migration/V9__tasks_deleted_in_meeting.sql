-- tasks module: the meeting during which a task was deleted, always one of the task's series. Null
-- for tasks deleted outside a meeting and for those deleted before this column existed.

ALTER TABLE task
  ADD COLUMN deleted_in_meeting_id uuid,
  ADD FOREIGN KEY (deleted_in_meeting_id, series_id) REFERENCES meeting (id, series_id);
