package com.meetflow.tasks.dto;

import com.meetflow.tasks.domain.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code decisionId} is null for a task that came straight from the agenda item, and {@code
 * deletion} for a task that hasn't been deleted.
 */
public record TaskResponse(
    UUID id,
    UUID teamId,
    UUID seriesId,
    UUID meetingId,
    UUID agendaItemId,
    @Nullable UUID decisionId,
    String description,
    UUID ownerId,
    String ownerName,
    LocalDate deadline,
    TaskStatus status,
    @Nullable Deletion deletion) {

  /** {@code meetingScheduledAt} is the meeting it was deleted in, null if outside a meeting. */
  public record Deletion(String byName, Instant at, @Nullable Instant meetingScheduledAt) {}
}
