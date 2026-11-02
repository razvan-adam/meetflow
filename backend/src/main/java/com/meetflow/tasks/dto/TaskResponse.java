package com.meetflow.tasks.dto;

import com.meetflow.tasks.domain.TaskStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code decisionId} is null for a task that came straight from the agenda item. */
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
    TaskStatus status) {}
