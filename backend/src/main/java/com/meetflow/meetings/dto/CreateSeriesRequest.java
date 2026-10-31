package com.meetflow.meetings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The limit matches the meeting_series table. */
public record CreateSeriesRequest(@NotBlank @Size(max = 100) String name) {}
