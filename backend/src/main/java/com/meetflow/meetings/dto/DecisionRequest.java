package com.meetflow.meetings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The text of a decision, to add it or edit it. The limit matches the decision table. */
public record DecisionRequest(@NotBlank @Size(max = 2000) String text) {}
