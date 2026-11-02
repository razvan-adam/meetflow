package com.meetflow.tasks.dto;

import com.meetflow.tasks.domain.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One line of a task's history. Only what changed is filled in; the creation has the new status,
 * owner and deadline and none of the old ones. {@code duringMeeting} says it was made while a
 * meeting of the series ran.
 */
public record TaskChangeResponse(
    Instant changedAt,
    String changedByName,
    boolean duringMeeting,
    @Nullable TaskStatus oldStatus,
    @Nullable TaskStatus newStatus,
    @Nullable String oldOwnerName,
    @Nullable String newOwnerName,
    @Nullable LocalDate oldDeadline,
    @Nullable LocalDate newDeadline,
    @Nullable String oldDescription,
    @Nullable String newDescription) {}
