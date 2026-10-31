package com.meetflow.notifications;

import java.util.UUID;

/**
 * An invitation to email. The token is only held in memory long enough to put it in the link; it is
 * never stored (docs/data-model.md).
 */
public record InvitationEmail(
    UUID invitationId, String recipient, String teamName, String inviterName, String token) {

  @Override
  public String toString() {
    return "InvitationEmail[invitationId=" + invitationId + "]";
  }
}
