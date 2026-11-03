package com.meetflow.minutes.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The minutes as people read them: the record of the meeting, with names filled in.
 *
 * @param emailRecipients how many participants the summary goes to
 * @param emailsSent how many of them it has reached so far
 */
public record MinutesResponse(
    UUID meetingId,
    UUID teamId,
    UUID seriesId,
    String seriesName,
    LocalDate meetingDate,
    LocalDate scheduledDate,
    Instant closedAt,
    String closedByName,
    List<Participant> participants,
    List<Task> commitments,
    List<MinutesContent.Item> agenda,
    List<Task> newTasks,
    int emailRecipients,
    long emailsSent) {

  /** {@code organizer} is whoever organized the series when the meeting closed. */
  public record Participant(UUID id, String name, boolean organizer) {}

  /**
   * The task as recorded, plus how it stands today, which is the one thing here that can change.
   *
   * @param removed whether it was removed during this meeting
   * @param removedSince whether it was removed after this meeting closed
   * @param currentStatus its status today
   */
  public record Task(
      UUID id,
      String description,
      String ownerName,
      LocalDate deadline,
      String statusBefore,
      String statusAtClose,
      long daysOverdue,
      boolean removed,
      boolean removedSince,
      String currentStatus) {}
}
