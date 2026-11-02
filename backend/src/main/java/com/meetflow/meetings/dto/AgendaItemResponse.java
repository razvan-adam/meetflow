package com.meetflow.meetings.dto;

import com.meetflow.meetings.domain.AgendaItemStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An agenda item; {@code carriedOver} means it wasn't covered at the previous meeting. */
public record AgendaItemResponse(
    UUID id, String title, @Nullable String notes, AgendaItemStatus status, boolean carriedOver) {}
