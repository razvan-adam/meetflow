package com.meetflow.teams.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Also used for an invitation of a team the user isn't a member of, so it isn't revealed. */
public class InvitationNotFoundException extends ErrorResponseException {

  public InvitationNotFoundException() {
    super(HttpStatus.NOT_FOUND);
    getBody().setDetail("Invitation not found.");
  }
}
