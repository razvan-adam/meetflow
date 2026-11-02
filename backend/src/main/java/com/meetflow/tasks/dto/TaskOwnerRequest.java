package com.meetflow.tasks.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TaskOwnerRequest(@NotNull UUID ownerId) {}
