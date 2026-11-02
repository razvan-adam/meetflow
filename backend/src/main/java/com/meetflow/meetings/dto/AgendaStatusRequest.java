package com.meetflow.meetings.dto;

import com.meetflow.meetings.domain.AgendaItemStatus;
import jakarta.validation.constraints.NotNull;

/** Whether an agenda item was covered; {@code PENDING} takes back an earlier mark. */
public record AgendaStatusRequest(@NotNull AgendaItemStatus status) {}
