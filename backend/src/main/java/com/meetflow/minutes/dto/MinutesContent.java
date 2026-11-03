package com.meetflow.minutes.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What {@code minutes.content} holds: the meeting as it was when it closed. People are kept by ID,
 * so their names are read when the minutes are shown and a deleted account appears anonymized.
 *
 * @param meetingDate the date it was actually held, in the organizer's time zone, fixed here so
 *     everyone reads the same date and the same overdue days
 * @param scheduledDate the date it was planned for, in the same time zone
 * @param commitments the tasks carried over to the meeting, with those removed during it
 */
public record MinutesContent(
    UUID seriesId,
    String seriesName,
    LocalDate meetingDate,
    LocalDate scheduledDate,
    Instant closedAt,
    UUID closedBy,
    List<UUID> participantIds,
    List<Task> commitments,
    List<Item> agenda,
    List<Task> newTasks) {

  /**
   * A task as it stood when the meeting closed.
   *
   * @param daysOverdue counted to the meeting's date, 0 if it wasn't overdue
   * @param removed whether it was removed during the meeting
   */
  public record Task(
      UUID id,
      String description,
      UUID ownerId,
      LocalDate deadline,
      String statusBefore,
      String statusAtClose,
      long daysOverdue,
      boolean removed) {}

  public record Item(
      String title,
      @Nullable String notes,
      boolean covered,
      boolean carriedOver,
      List<String> decisions) {}
}
