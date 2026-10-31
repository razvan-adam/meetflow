package com.meetflow.teams.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class AlreadyTeamMemberException extends ErrorResponseException {

  public AlreadyTeamMemberException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("This person is already a member of the team.");
  }
}
