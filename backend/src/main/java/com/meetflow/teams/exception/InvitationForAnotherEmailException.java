package com.meetflow.teams.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Only the invited address can join, so a forwarded or leaked link can't be used by others. */
public class InvitationForAnotherEmailException extends ErrorResponseException {

  public InvitationForAnotherEmailException() {
    super(HttpStatus.FORBIDDEN);
    getBody().setDetail("This invitation is for another email address.");
  }
}
