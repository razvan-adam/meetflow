package com.meetflow.tasks.dto;

import com.meetflow.tasks.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(@NotNull TaskStatus status) {}
