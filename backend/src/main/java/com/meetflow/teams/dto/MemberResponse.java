package com.meetflow.teams.dto;

import com.meetflow.teams.domain.Role;
import java.util.UUID;

/** A member of a team, as the team's other members see them. */
public record MemberResponse(UUID id, String name, Role role) {}
