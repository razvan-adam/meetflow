package com.meetflow.teams.exception;

import com.meetflow.teams.domain.Invitation;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** The link was already used or has expired. The status tells the app which message to show. */
public class InvitationUnusableException extends ErrorResponseException {

  public InvitationUnusableException(Invitation.Status status) {
    super(HttpStatus.GONE);
    getBody()
        .setDetail(
            status == Invitation.Status.ACCEPTED
                ? "This invitation was already used."
                : "This invitation has expired.");
    getBody().setProperty("invitationStatus", status);
  }
}
