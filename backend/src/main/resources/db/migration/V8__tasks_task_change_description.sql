-- tasks module: rewording a task during a later meeting goes in its history, because people have
-- been sent the task by then. The limits match task.description.

ALTER TABLE task_change
  ADD COLUMN old_description text CHECK (char_length(old_description) <= 500),
  ADD COLUMN new_description text CHECK (char_length(new_description) <= 500);
