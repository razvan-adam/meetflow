package com.meetflow.teams.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The limit matches the invitation table. */
public record InviteRequest(@NotBlank @Email @Size(max = 254) String email) {}
