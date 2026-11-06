package com.meetflow.tasks.dto;

import com.meetflow.tasks.domain.TaskStatus;
import java.time.LocalDate;
import java.util.UUID;

/** A task of the team that isn't done or removed, as the members page lists it per owner. */
public record OpenTaskResponse(
    UUID id, String description, UUID ownerId, LocalDate deadline, TaskStatus status) {}
