package com.meetflow.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** A new task: all three are required. The description's limit matches the task table. */
public record TaskRequest(
    @NotBlank @Size(max = 500) String description,
    @NotNull UUID ownerId,
    @NotNull LocalDate deadline) {}
