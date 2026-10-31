package com.meetflow.meetings.dto;

import java.util.UUID;

public record SeriesResponse(
    UUID id, UUID teamId, String name, UUID organizerId, String organizerName) {}
