package com.meetflow.meetings.dto;

import org.jspecify.annotations.Nullable;

/** {@code timeZone} is the organizer's, e.g. "Europe/Madrid"; without a valid one, UTC is used. */
public record CloseMeetingRequest(@Nullable String timeZone) {}
