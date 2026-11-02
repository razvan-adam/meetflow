package com.meetflow.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The limit matches the task table. */
public record TaskDescriptionRequest(@NotBlank @Size(max = 500) String description) {}
