package com.meetflow.meetings.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** The new organizer, a member of the series' team. */
public record ChangeOrganizerRequest(@NotNull UUID organizerId) {}
