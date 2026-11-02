package com.meetflow.meetings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The notes on an agenda item; empty clears them. The limit matches the agenda_item table. */
public record AgendaNotesRequest(@NotNull @Size(max = 10000) String notes) {}
