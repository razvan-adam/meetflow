package com.meetflow.notifications;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The task email (E3): each owner's tasks from a meeting. Its text is only held in memory while
 * sending; email bodies are never stored (docs/data-model.md).
 */
public record TaskSummaryEmail(
    UUID meetingId, String seriesName, LocalDate meetingDate, List<Owner> owners) {

  /** Someone who gets the email, with only their own tasks. */
  public record Owner(String email, List<Task> tasks) {}

  public record Task(UUID id, String description, LocalDate deadline) {}

  @Override
  public String toString() {
    return "TaskSummaryEmail[meetingId=" + meetingId + "]";
  }
}
