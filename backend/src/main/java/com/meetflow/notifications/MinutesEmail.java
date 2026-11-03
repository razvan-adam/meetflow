package com.meetflow.notifications;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The minutes email (E2): a short summary that links to the full minutes. Its text is only held in
 * memory while sending; email bodies are never stored (docs/data-model.md).
 *
 * @param commitments the open ones
 * @param removed the tasks removed during the meeting
 * @param carriedOver the agenda items that weren't covered
 */
public record MinutesEmail(
    UUID meetingId,
    String seriesName,
    LocalDate meetingDate,
    List<String> decisions,
    List<Task> newTasks,
    List<Task> commitments,
    List<String> removed,
    List<String> carriedOver,
    List<String> recipients) {

  /** {@code status} is as people read it, e.g. "in progress". */
  public record Task(
      String description, String ownerName, LocalDate deadline, String status, boolean overdue) {}

  @Override
  public String toString() {
    return "MinutesEmail[meetingId=" + meetingId + "]";
  }
}
