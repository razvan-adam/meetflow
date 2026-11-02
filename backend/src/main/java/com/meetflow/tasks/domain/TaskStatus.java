package com.meetflow.tasks.domain;

/** Overdue isn't a status: it is any task that isn't done and whose deadline has passed. */
public enum TaskStatus {
  OPEN,
  IN_PROGRESS,
  DONE
}
