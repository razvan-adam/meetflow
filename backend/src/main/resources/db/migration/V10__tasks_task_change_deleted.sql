-- tasks module: deleting a task is a line of its history too.

ALTER TABLE task_change ADD COLUMN deleted boolean NOT NULL DEFAULT false;
