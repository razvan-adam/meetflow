package com.meetflow.meetings;

import java.util.UUID;

/**
 * Published when the organizer closes a meeting, so other modules can react without the meetings
 * module depending on them: minutes generation (MF-22) and the task email (MF-20). Holds IDs only,
 * because events are stored in the database and meeting content must stay out of that table.
 */
public record MeetingClosedEvent(UUID meetingId, UUID teamId, UUID seriesId) {}
