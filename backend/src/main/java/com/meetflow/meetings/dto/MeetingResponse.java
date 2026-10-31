package com.meetflow.meetings.dto;

import com.meetflow.meetings.domain.MeetingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The participants are sorted by name. */
public record MeetingResponse(
    UUID id,
    UUID teamId,
    UUID seriesId,
    Instant scheduledAt,
    MeetingStatus status,
    List<ParticipantResponse> participants,
    long version) {}
