package com.meetflow.meetings.dto;

import java.util.UUID;

public record DecisionResponse(UUID id, UUID agendaItemId, String text) {}
