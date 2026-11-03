package com.meetflow.tasks.dto;

import com.meetflow.tasks.domain.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A task on its owner's dashboard, with the names of where it belongs. */
public record PendingTaskResponse(
    UUID id,
    String description,
    LocalDate deadline,
    TaskStatus status,
    UUID teamId,
    String teamName,
    UUID seriesId,
    String seriesName,
    UUID meetingId,
    Instant meetingScheduledAt) {}
