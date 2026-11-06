package com.meetflow.teams.dto;

import com.meetflow.teams.domain.Role;
import jakarta.validation.constraints.NotNull;

public record RoleRequest(@NotNull Role role) {}
