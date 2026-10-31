package com.meetflow.meetings.domain;

/** A meeting is scheduled, then started, then closed by its organizer (docs/core-flow.md). */
public enum MeetingStatus {
  SCHEDULED,
  IN_PROGRESS,
  CLOSED
}
