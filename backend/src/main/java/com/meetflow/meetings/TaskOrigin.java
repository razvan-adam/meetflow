package com.meetflow.meetings;

import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Where a new task comes from: an agenda item of a running meeting and, if it was agreed as a
 * decision, that decision. Holds what the tasks module needs to create and check the task.
 */
public record TaskOrigin(
    UUID teamId,
    UUID seriesId,
    UUID meetingId,
    UUID agendaItemId,
    @Nullable UUID decisionId,
    Set<UUID> participantIds) {}
