package com.meetflow.teams.dto;

import com.meetflow.teams.domain.Invitation;

/**
 * What the holder of an invitation link sees before joining: the team, who invited them, the
 * invited address (fixed in the sign-up form) and whether the link still works.
 */
public record InvitationLinkResponse(
    String teamName, String invitedBy, String email, Invitation.Status status) {}
