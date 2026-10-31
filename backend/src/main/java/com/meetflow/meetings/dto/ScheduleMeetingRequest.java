package com.meetflow.meetings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The participants are team members; the organizer always attends, listed or not. */
public record ScheduleMeetingRequest(
    @NotNull Instant scheduledAt, @NotNull @Size(max = 500) List<@NotNull UUID> participantIds) {}
