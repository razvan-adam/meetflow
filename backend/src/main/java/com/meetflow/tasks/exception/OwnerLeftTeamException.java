package com.meetflow.tasks.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** A done task whose owner has left the team can't be reopened until it has a new owner. */
public class OwnerLeftTeamException extends ErrorResponseException {

  public OwnerLeftTeamException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("This task's owner is no longer in the team. Reassign it first.");
  }
}
