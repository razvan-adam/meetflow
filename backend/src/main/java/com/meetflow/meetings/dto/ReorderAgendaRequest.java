package com.meetflow.meetings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Every item of the agenda, in the new order. */
public record ReorderAgendaRequest(@NotNull @Size(max = 500) List<@NotNull UUID> itemIds) {}
