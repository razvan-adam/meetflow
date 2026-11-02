package com.meetflow.tasks.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TaskDeadlineRequest(@NotNull LocalDate deadline) {}
