package com.meetflow.teams.dto;

import java.time.Instant;
import java.util.UUID;

/** A pending invitation, as team admins see it. */
public record InvitationResponse(
    UUID id, String email, String invitedBy, Instant createdAt, Instant expiresAt) {}
