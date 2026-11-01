package com.meetflow.meetings.domain;

/** Whether an item was covered in the meeting. It stays pending until the meeting runs (MF-15). */
public enum AgendaItemStatus {
  PENDING,
  COVERED,
  NOT_COVERED
}
