package com.meetflow.meetings;

import java.util.UUID;

/**
 * Published when the organizer schedules a meeting, so the tasks module can put the series' open
 * tasks on it without the meetings module depending on tasks (MF-19).
 */
public record MeetingScheduledEvent(UUID meetingId, UUID seriesId) {}
