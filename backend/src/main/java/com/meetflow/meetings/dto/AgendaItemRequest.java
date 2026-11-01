package com.meetflow.meetings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The title of an agenda item, to add it or rename it. The limit matches the agenda_item table. */
public record AgendaItemRequest(@NotBlank @Size(max = 200) String title) {}
