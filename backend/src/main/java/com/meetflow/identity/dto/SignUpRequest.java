package com.meetflow.identity.dto;

import com.meetflow.identity.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Only the name and email are collected (GDPR). Limits match the app_user table. */
public record SignUpRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull
        @Size(
            min = UserService.MIN_PASSWORD_LENGTH,
            message = "must be at least {min} characters long")
        @MaxBytes(72)
        String password) {}
