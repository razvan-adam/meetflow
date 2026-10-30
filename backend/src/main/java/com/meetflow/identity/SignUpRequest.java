package com.meetflow.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Only the name and email are collected (GDPR). Limits match the app_user table. */
record SignUpRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull @Size(min = UserService.MIN_PASSWORD_LENGTH) @MaxBytes(72) String password) {}
