package com.meetflow.teams.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Also used for a team the user isn't a member of, so its existence isn't revealed. */
public class TeamNotFoundException extends ErrorResponseException {

  public TeamNotFoundException() {
    super(HttpStatus.NOT_FOUND);
    getBody().setDetail("Team not found.");
  }
}
