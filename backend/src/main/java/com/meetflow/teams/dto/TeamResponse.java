package com.meetflow.teams.dto;

import com.meetflow.teams.domain.Role;
import java.util.UUID;

/** A team as the current user sees it, with their role in it. */
public record TeamResponse(UUID id, String name, Role role) {}
