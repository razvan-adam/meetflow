package com.meetflow.meetings.dto;

import com.meetflow.meetings.domain.MeetingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The participants are sorted by name. {@code closedAt} is set once the meeting is closed. */
public record MeetingResponse(
    UUID id,
    UUID teamId,
    UUID seriesId,
    Instant scheduledAt,
    MeetingStatus status,
    @Nullable Instant closedAt,
    List<ParticipantResponse> participants) {}
