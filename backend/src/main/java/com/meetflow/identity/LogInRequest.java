package com.meetflow.identity;

import jakarta.validation.constraints.NotNull;

/** No format rules: a wrong email or password just fails to log in. */
record LogInRequest(@NotNull String email, @NotNull String password) {}
