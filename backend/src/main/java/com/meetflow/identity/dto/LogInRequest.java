package com.meetflow.identity.dto;

import jakarta.validation.constraints.NotNull;

/** No format rules: a wrong email or password just fails to log in. */
public record LogInRequest(@NotNull String email, @NotNull String password) {}
