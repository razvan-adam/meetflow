package com.meetflow.meetings.dto;

import java.util.UUID;

/** An agenda item; {@code carriedOver} means it wasn't covered at the previous meeting. */
public record AgendaItemResponse(UUID id, String title, boolean carriedOver) {}
