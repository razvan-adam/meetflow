package com.meetflow.meetings;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A closed meeting as it stands, for its minutes. Only the series organizer can close a meeting, so
 * {@code closedBy} is also the organizer it had at that moment.
 */
public record MeetingRecord(
    UUID id,
    UUID teamId,
    UUID seriesId,
    String seriesName,
    Instant scheduledAt,
    Instant startedAt,
    Instant closedAt,
    UUID closedBy,
    Set<UUID> participantIds,
    List<Item> agenda) {

  /** An agenda item with its decisions, oldest first. */
  public record Item(
      String title,
      @Nullable String notes,
      boolean covered,
      boolean carriedOver,
      List<String> decisions) {}
}
