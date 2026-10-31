package com.meetflow.meetings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Like {@link ScheduleMeetingRequest}, plus the version of the meeting the edit was made on, so an
 * edit of an outdated copy is rejected.
 */
public record UpdateMeetingRequest(
    @NotNull Instant scheduledAt,
    @NotNull @Size(max = 500) List<@NotNull UUID> participantIds,
    @NotNull Long version) {}
