package com.meetflow.teams.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The limit matches the team table. */
public record CreateTeamRequest(@NotBlank @Size(max = 100) String name) {}
